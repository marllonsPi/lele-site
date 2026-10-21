package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.cadastros.NumeroCnj;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Do bruto a fila "A classificar" (arquitetura 5). Idempotente: o mesmo item nunca entra duas vezes; itens da mesma
 * publicacao vindos de fontes diferentes viram uma publicacao com varias fontes; o que o filtro ignora fica
 * registrado em item_ignorado.
 */
@Service
public class PipelineCaptura {

	public record Resultado(int lidos, int duplicados, int ignorados, int novasPublicacoes, int mescladas) {

		static Resultado soma(Resultado a, Resultado b) {
			return new Resultado(a.lidos + b.lidos, a.duplicados + b.duplicados, a.ignorados + b.ignorados,
					a.novasPublicacoes + b.novasPublicacoes, a.mescladas + b.mescladas);
		}

		public static Resultado vazio() {
			return new Resultado(0, 0, 0, 0, 0);
		}
	}

	private final PublicacaoBrutaRepository brutas;
	private final PublicacaoRepository publicacoes;
	private final FiltroCapturaRepository filtros;
	private final CadastroService cadastros;
	private final JdbcTemplate jdbc;

	PipelineCaptura(PublicacaoBrutaRepository brutas, PublicacaoRepository publicacoes, FiltroCapturaRepository filtros,
			CadastroService cadastros, JdbcTemplate jdbc) {
		this.brutas = brutas;
		this.publicacoes = publicacoes;
		this.filtros = filtros;
		this.cadastros = cadastros;
		this.jdbc = jdbc;
	}

	public Resultado processar(Long titularId, List<DadosBrutos> itens) {
		return processar(titularId, null, itens);
	}

	/** {@code oab}: a inscricao que originou a busca (null quando a fonte nao e por OAB); filtros de uma OAB so valem para ela. */
	@Transactional
	public Resultado processar(Long titularId, OabMonitorada oab, List<DadosBrutos> itens) {
		Resultado total = Resultado.vazio();
		for (DadosBrutos d : itens) {
			total = Resultado.soma(total, um(titularId, oab, d));
		}
		return total;
	}

	private Resultado um(Long titularId, OabMonitorada oab, DadosBrutos d) {
		if (brutas.existsByFonteAndIdExternoAndTitularId(d.fonte(), d.idExterno(), titularId)) {
			return new Resultado(1, 1, 0, 0, 0);
		}
		PublicacaoBruta bruta = brutas.save(new PublicacaoBruta(titularId, d));
		if (d.metadados() != null) {
			jdbc.update("update publicacao_bruta set metadados = ? where id = ?", MetadadosJson.escrever(d.metadados()), bruta.getId());
		}
		for (FiltroCaptura f : filtros.findByUsuarioIdAndFonteAndAtivoTrue(titularId, d.fonte())) {
			if (f.getOabNumero() != null && (oab == null || !f.getOabNumero().equals(oab.numero()) || !f.getOabUf().equalsIgnoreCase(oab.uf()))) {
				continue; // filtro de uma OAB especifica: nao vale para itens de outra
			}
			String motivo = motivoDeIgnorar(f, d);
			if (motivo != null) {
				jdbc.update("insert into item_ignorado (bruta_id, regra, motivo) values (?, ?, ?)", bruta.getId(), "FILTRO_" + f.getId(),
						motivo.length() > 300 ? motivo.substring(0, 300) : motivo);
				return new Resultado(1, 0, 1, 0, 0);
			}
		}
		String numero = chave(d);
		var existente = publicacoes.findByTitularIdAndNumeroProcessoAndDataPublicacao(titularId, numero, d.dataPublicacao());
		boolean nova = existente.isEmpty();
		Publicacao p = existente.orElseGet(() -> publicacoes.save(new Publicacao(numero, titularId, d.dataPublicacao(),
				d.tribunal(), resumo(d.conteudo()), cadastros.processoPorNumero(numero).map(r -> r.id()).orElse(null))));
		jdbc.update("insert into publicacao_fonte (publicacao_id, fonte, bruta_id) values (?, ?, ?) on conflict do nothing",
				p.getId(), d.fonte().name(), bruta.getId());
		return new Resultado(1, 0, 0, nova ? 1 : 0, nova ? 0 : 1);
	}

	private static String motivoDeIgnorar(FiltroCaptura f, DadosBrutos d) {
		String daOab = f.getOabNumero() == null ? "" : " [OAB " + f.getOabNumero() + "/" + f.getOabUf() + "]";
		if ("SO_UF".equals(f.getModo())) {
			if (Filtros.ignoraForaDasUfs(f.getUfs(), d.numeroProcesso(), d.tribunal())) {
				return "Processo de fora de " + f.getUfs() + " (tribunal " + d.tribunal() + ")" + daOab;
			}
			return null;
		}
		if (Filtros.ignora(d.tribunal(), f.getTribunalPrefixo(), f.getExcecoes())) {
			return "Tribunal " + d.tribunal() + " ignorado pelo filtro " + f.getTribunalPrefixo()
					+ (f.getExcecoes() == null ? "" : " (exceto " + f.getExcecoes() + ")") + daOab;
		}
		return null;
	}

	/** Numero CNJ normalizado; sem numero valido usa o hash do conteudo, para nao misturar publicacoes distintas. */
	static String chave(DadosBrutos d) {
		String n = NumeroCnj.normalizar(d.numeroProcesso());
		return n.length() == 20 ? n : "SN-" + d.hash().substring(0, Math.min(16, d.hash().length()));
	}

	static String resumo(String conteudo) {
		String limpo = conteudo.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
		return limpo.length() <= 400 ? limpo : limpo.substring(0, 397) + "...";
	}
}
