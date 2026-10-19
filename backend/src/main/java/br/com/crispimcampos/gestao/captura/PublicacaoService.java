package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.agenda.PrazoDto;
import br.com.crispimcampos.gestao.agenda.PrazoService;
import br.com.crispimcampos.gestao.agenda.TipoAgendamento;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.sql.Array;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fila "A classificar" (item 3). Leitura por Visibilidade no escopo PUBLICACAO (invariante 1); so socias classificam:
 * definem tipo, responsavel e prazos (o prazo nasce aguardando a conferencia de outra pessoa) ou descartam com motivo.
 */
@Service
public class PublicacaoService {

	private final PublicacaoRepository publicacoes;
	private final Visibilidade visibilidade;
	private final PrazoService prazos;
	private final CadastroService cadastros;
	private final AuditoriaService auditoria;
	private final JdbcTemplate jdbc;
	private final org.springframework.context.ApplicationEventPublisher eventos;

	PublicacaoService(PublicacaoRepository publicacoes, Visibilidade visibilidade, PrazoService prazos,
			CadastroService cadastros, AuditoriaService auditoria, JdbcTemplate jdbc,
			org.springframework.context.ApplicationEventPublisher eventos) {
		this.publicacoes = publicacoes;
		this.visibilidade = visibilidade;
		this.prazos = prazos;
		this.cadastros = cadastros;
		this.auditoria = auditoria;
		this.jdbc = jdbc;
		this.eventos = eventos;
	}

	@Transactional(readOnly = true)
	public List<PublicacaoDto> listar(UsuarioLogado u, EstadoPublicacao estado) {
		Set<Long> titulares = visibilidade.titularesVisiveis(u, Escopo.PUBLICACAO);
		if (titulares.isEmpty()) {
			return List.of();
		}
		List<Publicacao> lista = estado == null ? publicacoes.findByTitularIdInOrderByDataPublicacaoDescIdDesc(titulares)
				: publicacoes.findByTitularIdInAndEstadoOrderByDataPublicacaoDescIdDesc(titulares, estado);
		Map<Long, List<Fonte>> fontes = fontesDe(lista.stream().map(Publicacao::getId).toList());
		return lista.stream().map(p -> PublicacaoDto.de(p, fontes.getOrDefault(p.getId(), List.of()), null)).toList();
	}

	/** Quantas aguardam classificacao (painel das socias). */
	@Transactional(readOnly = true)
	public long contarAClassificar(UsuarioLogado u) {
		Set<Long> titulares = visibilidade.titularesVisiveis(u, Escopo.PUBLICACAO);
		return titulares.isEmpty() ? 0 : publicacoes.countByTitularIdInAndEstado(titulares, EstadoPublicacao.A_CLASSIFICAR);
	}

	@Transactional(readOnly = true)
	public PublicacaoDto buscar(UsuarioLogado u, Long id) {
		Publicacao p = visivel(u, id);
		String conteudo = jdbc.query("select b.conteudo from publicacao_fonte f join publicacao_bruta b on b.id = f.bruta_id "
				+ "where f.publicacao_id = ? order by b.id limit 1", rs -> rs.next() ? rs.getString(1) : null, id);
		return PublicacaoDto.de(p, fontesDe(List.of(id)).getOrDefault(id, List.of()), conteudo);
	}

	@Transactional
	public PublicacaoDto vincularProcesso(UsuarioLogado u, Long id, Long processoId) {
		Publicacao p = daSocia(u, id);
		cadastros.resumo(processoId);
		p.vincularProcesso(processoId);
		auditoria.registrar(u.id(), "PUBLICACAO_VINCULADA", "publicacao", String.valueOf(id), null);
		return PublicacaoDto.de(p, fontesDe(List.of(id)).getOrDefault(id, List.of()), null);
	}

	/** Classifica como prazo: cria o agendamento (aguarda conferencia) e fecha a publicacao. */
	@Transactional
	public PublicacaoDto classificar(UsuarioLogado u, Long id, TipoAgendamento tipo, Long processoId, Long responsavelId,
			boolean urgente, String descricao, LocalDate inicioPrazo, LocalDate prazoFatal) {
		Publicacao p = daSocia(u, id);
		Long processo = processoId != null ? processoId : p.getProcessoId();
		if (processo == null) {
			throw new IllegalArgumentException("Cadastre ou vincule o processo antes de classificar");
		}
		PrazoDto prazo = prazos.lancar(u, processo, tipo, descricao != null ? descricao : p.getResumo(), responsavelId,
				urgente, inicioPrazo, prazoFatal);
		if (p.getProcessoId() == null) {
			p.vincularProcesso(processo);
		}
		p.classificar(prazo.id());
		auditoria.registrar(u.id(), "PUBLICACAO_CLASSIFICADA", "publicacao", String.valueOf(id), "tipo=" + tipo);
		eventos.publishEvent(new PublicacaoClassificada(id, processo, p.getDataPublicacao(), p.getResumo(), u.id()));
		return PublicacaoDto.de(p, fontesDe(List.of(id)).getOrDefault(id, List.of()), null);
	}

	@Transactional
	public PublicacaoDto descartar(UsuarioLogado u, Long id, String motivo) {
		Publicacao p = daSocia(u, id);
		if (motivo == null || motivo.isBlank()) {
			throw new IllegalArgumentException("Informe o motivo do descarte");
		}
		p.descartar(motivo.trim());
		auditoria.registrar(u.id(), "PUBLICACAO_DESCARTADA", "publicacao", String.valueOf(id), null);
		return PublicacaoDto.de(p, fontesDe(List.of(id)).getOrDefault(id, List.of()), null);
	}

	private Publicacao daSocia(UsuarioLogado u, Long id) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("Somente socias classificam publicacoes");
		}
		return visivel(u, id);
	}

	private Publicacao visivel(UsuarioLogado u, Long id) {
		Publicacao p = publicacoes.findById(id).orElseThrow(() -> new CapturaNaoEncontradaException("Publicacao nao encontrada"));
		if (!visibilidade.podeVer(u, Escopo.PUBLICACAO, p.getTitularId())) {
			throw new CapturaNaoEncontradaException("Publicacao nao encontrada");
		}
		return p;
	}

	private Map<Long, List<Fonte>> fontesDe(List<Long> ids) {
		Map<Long, List<Fonte>> mapa = new HashMap<>();
		if (ids.isEmpty()) {
			return mapa;
		}
		jdbc.query(con -> {
			var ps = con.prepareStatement("select publicacao_id, fonte from publicacao_fonte where publicacao_id = any(?) order by fonte");
			Array a = con.createArrayOf("bigint", ids.toArray());
			ps.setArray(1, a);
			return ps;
		}, rs -> {
			mapa.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(Fonte.valueOf(rs.getString(2)));
		});
		return mapa;
	}
}
