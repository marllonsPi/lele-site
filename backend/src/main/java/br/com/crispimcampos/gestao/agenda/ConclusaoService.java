package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conclusao e conferencia (item 10). Fluxo: responsavel finaliza -> (estagiario: supervisor) -> socia marca
 * Conferido ou Devolvido com motivo (volta ao responsavel). Cada decisao fica em `conferencia` (imutavel) e a
 * devolucao tambem entra no historico de observacoes do item.
 */
@Service
public class ConclusaoService {

	private final AgendamentoRepository agendamentos;
	private final ConferenciaRepository conferencias;
	private final ObservacaoRepository observacoes;
	private final RegrasAgendamento regras;
	private final Visibilidade visibilidade;
	private final UsuarioService usuarios;
	private final AuditoriaService auditoria;
	private final Clock relogio;

	ConclusaoService(AgendamentoRepository agendamentos, ConferenciaRepository conferencias,
			ObservacaoRepository observacoes, RegrasAgendamento regras, Visibilidade visibilidade,
			UsuarioService usuarios, AuditoriaService auditoria, Clock relogio) {
		this.agendamentos = agendamentos;
		this.conferencias = conferencias;
		this.observacoes = observacoes;
		this.regras = regras;
		this.visibilidade = visibilidade;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/** O responsavel (ou uma socia) conclui o item. */
	@Transactional
	public AgendaItemDto finalizar(UsuarioLogado ator, Long id) {
		Agendamento a = regras.obterVisivel(ator, id);
		if (!ator.id().equals(a.getResponsavelId()) && ator.perfil() != Perfil.SOCIA) {
			throw RegrasAgendamento.proibido("Somente o responsavel ou uma socia finaliza o item");
		}
		if (a.getStatus() == StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO) {
			throw RegrasAgendamento.regra("O prazo ainda aguarda a conferencia da contagem");
		}
		if (a.getStatus() != StatusAgendamento.PENDENTE && a.getStatus() != StatusAgendamento.DEVOLVIDO) {
			throw RegrasAgendamento.regra("O item ja foi finalizado");
		}
		boolean responsavelEstagiario = ator.id().equals(a.getResponsavelId()) ? ator.perfil() == Perfil.ESTAGIARIO
				: usuarios.buscar(a.getResponsavelId()).perfil() == Perfil.ESTAGIARIO;
		a.finalizar(ator.id(), responsavelEstagiario, relogio.instant());
		auditoria.registrar(ator.id(), "AGENDAMENTO_FINALIZADO", "agendamento", String.valueOf(id), null);
		return item(a);
	}

	/** Supervisor do estagiario (ou socia) aprova e envia as socias. */
	@Transactional
	public AgendaItemDto aprovarSupervisor(UsuarioLogado ator, Long id) {
		Agendamento a = doSupervisor(ator, id);
		a.aprovarPeloSupervisor();
		conferencias.save(new Conferencia(id, Conferencia.Etapa.SUPERVISOR, Conferencia.Resultado.CONFERIDO, null, ator.id()));
		auditoria.registrar(ator.id(), "CONFERENCIA_SUPERVISOR", "agendamento", String.valueOf(id), "CONFERIDO");
		return item(a);
	}

	@Transactional
	public AgendaItemDto devolverSupervisor(UsuarioLogado ator, Long id, String motivo) {
		Agendamento a = doSupervisor(ator, id);
		return devolver(ator, a, Conferencia.Etapa.SUPERVISOR, motivo);
	}

	@Transactional
	public AgendaItemDto conferirSocia(UsuarioLogado ator, Long id) {
		Agendamento a = daSocia(ator, id);
		a.conferirPelaSocia();
		conferencias.save(new Conferencia(id, Conferencia.Etapa.SOCIA, Conferencia.Resultado.CONFERIDO, null, ator.id()));
		auditoria.registrar(ator.id(), "CONFERENCIA_SOCIA", "agendamento", String.valueOf(id), "CONFERIDO");
		return item(a);
	}

	@Transactional
	public AgendaItemDto devolverSocia(UsuarioLogado ator, Long id, String motivo) {
		return devolver(ator, daSocia(ator, id), Conferencia.Etapa.SOCIA, motivo);
	}

	@Transactional(readOnly = true)
	public List<ConferenciaDto> historico(UsuarioLogado ator, Long id) {
		regras.obterVisivel(ator, id);
		return conferencias.findByAgendamentoIdOrderByIdAsc(id).stream().map(ConferenciaDto::de).toList();
	}

	/**
	 * Fila de conferencia: socia ve o que aguarda socias (nos titulares visiveis); supervisor ve o que aguarda ele
	 * (itens dos seus estagiarios). Outros perfis recebem lista vazia.
	 */
	@Transactional(readOnly = true)
	public List<AgendaItemDto> pendentesDeConferencia(UsuarioLogado ator) {
		LocalDate hoje = LocalDate.now(relogio);
		List<Agendamento> itens = new ArrayList<>();
		if (ator.perfil() == Perfil.SOCIA) {
			Set<Long> titulares = visibilidade.titularesVisiveis(ator, Escopo.AGENDAMENTO);
			if (!titulares.isEmpty()) {
				itens.addAll(agendamentos.findByTitularIdInAndStatusIn(titulares, Set.of(StatusAgendamento.EM_CONFERENCIA_SOCIA)));
			}
		}
		if (ator.perfil() == Perfil.ADVOGADO || ator.perfil() == Perfil.SOCIA) {
			Set<Long> estagiarios = new java.util.HashSet<>();
			for (UsuarioDto u : usuarios.listar()) {
				if (u.perfil() == Perfil.ESTAGIARIO && ator.id().equals(u.supervisorId())) {
					estagiarios.add(u.id());
				}
			}
			if (!estagiarios.isEmpty()) {
				itens.addAll(agendamentos.findByResponsavelIdInAndStatus(estagiarios, StatusAgendamento.EM_CONFERENCIA_SUPERVISOR));
			}
		}
		return itens.stream().map(a -> AgendaItemDto.de(a, hoje, relogio.getZone())).sorted(AgendaService.ORDEM).toList();
	}

	// ---- internos

	private AgendaItemDto devolver(UsuarioLogado ator, Agendamento a, Conferencia.Etapa etapa, String motivo) {
		if (motivo == null || motivo.isBlank()) {
			throw RegrasAgendamento.regra("Informe o motivo da devolucao");
		}
		a.devolver();
		String m = motivo.trim();
		conferencias.save(new Conferencia(a.getId(), etapa, Conferencia.Resultado.DEVOLVIDO, m, ator.id()));
		observacoes.save(new ObservacaoAgendamento(a.getId(), ator.id(), "Devolvido (" + etapa.name().toLowerCase() + "): " + m));
		auditoria.registrar(ator.id(), "DEVOLVIDO_" + etapa.name(), "agendamento", String.valueOf(a.getId()), null);
		return item(a);
	}

	private Agendamento daSocia(UsuarioLogado ator, Long id) {
		if (ator.perfil() != Perfil.SOCIA) {
			throw RegrasAgendamento.proibido("Somente socias conferem");
		}
		return regras.obterVisivel(ator, id);
	}

	/** Supervisor do estagiario responsavel pelo item (ou socia que o ve). */
	private Agendamento doSupervisor(UsuarioLogado ator, Long id) {
		Agendamento a = agendamentos.findById(id).orElseThrow(() -> new AgendaNaoEncontradaException("Agendamento nao encontrado"));
		UsuarioDto responsavel = usuarios.buscar(a.getResponsavelId());
		boolean supervisor = ator.id().equals(responsavel.supervisorId());
		if (!supervisor && !(ator.perfil() == Perfil.SOCIA && regras.visivel(ator, a))) {
			throw new AgendaNaoEncontradaException("Agendamento nao encontrado");
		}
		if (a.getStatus() != StatusAgendamento.EM_CONFERENCIA_SUPERVISOR) {
			throw RegrasAgendamento.regra("O item nao aguarda o supervisor");
		}
		return a;
	}

	private AgendaItemDto item(Agendamento a) {
		return AgendaItemDto.de(a, LocalDate.now(relogio), relogio.getZone());
	}
}
