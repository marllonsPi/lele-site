package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.cadastros.ProcessoResumo;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audiencias (AUD) e atendimentos (ATD): data e hora proprias, sem a regra dos 3 dias uteis (invariante 5).
 * Conflitos de horario sao avisados na resposta, nunca bloqueiam (pendencia 5).
 */
@Service
public class CompromissoService {

	static final int DURACAO_PADRAO_AUDIENCIA_MIN = 60;

	private final AgendamentoRepository agendamentos;
	private final CadastroService cadastros;
	private final Visibilidade visibilidade;
	private final RegrasAgendamento regras;
	private final AuditoriaService auditoria;
	private final Clock relogio;

	CompromissoService(AgendamentoRepository agendamentos, CadastroService cadastros, Visibilidade visibilidade,
			RegrasAgendamento regras, AuditoriaService auditoria, Clock relogio) {
		this.agendamentos = agendamentos;
		this.cadastros = cadastros;
		this.visibilidade = visibilidade;
		this.regras = regras;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	public record Marcado(AgendaItemDto item, List<Conflitos.Aviso> avisos) {
	}

	/**
	 * @param responsavelOuAtendenteId AUD: responsavel pela audiencia; ATD: advogado que atendera (e o responsavel)
	 */
	@Transactional
	public Marcado marcar(UsuarioLogado ator, TipoAgendamento tipo, Long processoId, Long clienteId, String descricao,
			Long responsavelOuAtendenteId, boolean urgente, LocalDateTime inicioLocal, Integer duracaoMin,
			Modalidade modalidade, String localOuLink) {
		if (tipo.ehPrazo()) {
			throw RegrasAgendamento.regra("Use o lancamento de prazos para este tipo");
		}
		if (ator.perfil() == Perfil.ESTAGIARIO) {
			throw RegrasAgendamento.proibido("Estagiario nao marca compromissos");
		}
		if (inicioLocal == null || modalidade == null) {
			throw RegrasAgendamento.regra("Informe data, hora e modalidade");
		}
		boolean atd = tipo == TipoAgendamento.ATD;
		validarFormato(tipo, modalidade, localOuLink, duracaoMin);

		ProcessoResumo processo = null;
		if (processoId != null) {
			processo = cadastros.resumo(processoId);
			if (!processo.ativo()) {
				throw RegrasAgendamento.regra("Processo desativado");
			}
			if (!visibilidade.podeVer(ator, Escopo.AGENDAMENTO, processo.titularId())) {
				throw new AgendaNaoEncontradaException("Processo nao encontrado");
			}
		} else if (!atd) {
			throw RegrasAgendamento.regra("Audiencia exige processo");
		}

		Long cliente = clienteId;
		if (atd) {
			if (cliente == null && processo != null) {
				cliente = processo.clienteId();
			}
			if (cliente == null) {
				throw RegrasAgendamento.regra("Atendimento exige cliente");
			}
			if (processo != null && !cliente.equals(processo.clienteId())) {
				throw RegrasAgendamento.regra("O cliente do atendimento deve ser o do processo");
			}
			if (!cadastros.clienteExisteAtivo(cliente)) {
				throw RegrasAgendamento.regra("Cliente nao encontrado ou desativado");
			}
		} else {
			cliente = null;
		}

		UsuarioDto responsavel = regras.validarResponsavel(ator, responsavelOuAtendenteId);
		if (atd && responsavel.perfil() != Perfil.SOCIA && responsavel.perfil() != Perfil.ADVOGADO) {
			throw RegrasAgendamento.regra("O atendimento deve ser feito por advogado ou socia");
		}
		Long titular = processo != null ? processo.titularId() : responsavelOuAtendenteId;
		if (processo == null && !visibilidade.podeVer(ator, Escopo.AGENDAMENTO, titular)) {
			throw RegrasAgendamento.proibido("Voce nao pode agendar atendimento para este advogado");
		}

		int duracao = duracaoMin != null ? duracaoMin : DURACAO_PADRAO_AUDIENCIA_MIN;
		Instant inicio = inicioLocal.atZone(relogio.getZone()).toInstant();
		List<Conflitos.Aviso> avisos = Conflitos.detectar(
				new Conflitos.Intervalo(null, tipo, responsavelOuAtendenteId, inicio, inicio.plus(Duration.ofMinutes(duracao))),
				existentesProximos(inicio, duracao));

		Agendamento a = agendamentos.save(Agendamento.marcarCompromisso(processoId, titular, tipo, limpo(descricao),
				responsavelOuAtendenteId, ator.id(), urgente, inicio, duracao, modalidade, limpo(localOuLink), cliente,
				atd ? responsavelOuAtendenteId : null));
		auditoria.registrar(ator.id(), "COMPROMISSO_MARCADO", "agendamento", String.valueOf(a.getId()), "tipo=" + tipo);
		return new Marcado(AgendaItemDto.de(a, java.time.LocalDate.now(relogio), relogio.getZone()), avisos);
	}

	private List<Conflitos.Intervalo> existentesProximos(Instant inicio, int duracao) {
		Instant de = inicio.minus(Duration.ofHours(24));
		Instant ate = inicio.plus(Duration.ofMinutes(duracao));
		return agendamentos.findCompromissosIniciandoEntre(List.of(TipoAgendamento.AUD, TipoAgendamento.ATD), de, ate)
				.stream().map(e -> new Conflitos.Intervalo(e.getId(), e.getTipo(),
						e.getTipo() == TipoAgendamento.ATD ? e.getAdvogadoAtendenteId() : e.getResponsavelId(),
						e.getInicioEm(), e.getInicioEm().plus(Duration.ofMinutes(e.getDuracaoMin()))))
				.toList();
	}

	private static void validarFormato(TipoAgendamento tipo, Modalidade modalidade, String local, Integer duracao) {
		if (tipo == TipoAgendamento.AUD && modalidade == Modalidade.TELEFONE) {
			throw RegrasAgendamento.regra("Audiencia e presencial ou online");
		}
		if (tipo == TipoAgendamento.ATD && (duracao == null || duracao <= 0)) {
			throw RegrasAgendamento.regra("Atendimento exige duracao");
		}
		if (duracao != null && (duracao <= 0 || duracao > 24 * 60)) {
			throw RegrasAgendamento.regra("Duracao invalida");
		}
		if (modalidade != Modalidade.TELEFONE && (local == null || local.isBlank())) {
			throw RegrasAgendamento.regra(modalidade == Modalidade.ONLINE ? "Informe o link" : "Informe o local");
		}
	}

	private static String limpo(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
