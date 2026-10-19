package br.com.crispimcampos.gestao.calendario;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feriados e suspensoes editaveis (item 6). Dia nao util = fim de semana, feriado ou suspensao aplicavel ao
 * contexto (nacional sempre; tribunal/estado/municipio quando a referencia confere).
 * Por seguranca, recusa calcular em periodo sem nenhum feriado nacional cadastrado: calendario vazio
 * produziria prazo interno errado em silencio.
 */
@Service
public class CalendarioService {

	private final FeriadoRepository feriados;
	private final AuditoriaService auditoria;

	CalendarioService(FeriadoRepository feriados, AuditoriaService auditoria) {
		this.feriados = feriados;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<FeriadoDto> listar() {
		return feriados.findAllByOrderByDataInicialAsc().stream().map(FeriadoDto::de).toList();
	}

	@Transactional
	public FeriadoDto criar(Long atorId, EscopoFeriado escopo, String referencia, TipoFeriado tipo, LocalDate inicial,
			LocalDate fim, String descricao) {
		Feriado f = new Feriado(escopo, ref(escopo, referencia), tipo, inicial, validarFim(inicial, fim), descricao.trim());
		f = feriados.save(f);
		auditoria.registrar(atorId, "FERIADO_CRIADO", "feriado", String.valueOf(f.getId()), null);
		return FeriadoDto.de(f);
	}

	@Transactional
	public FeriadoDto atualizar(Long atorId, Long id, EscopoFeriado escopo, String referencia, TipoFeriado tipo,
			LocalDate inicial, LocalDate fim, String descricao) {
		Feriado f = feriados.findById(id).orElseThrow(() -> new CalendarioException("Feriado nao encontrado"));
		f.atualizar(escopo, ref(escopo, referencia), tipo, inicial, validarFim(inicial, fim), descricao.trim());
		auditoria.registrar(atorId, "FERIADO_ATUALIZADO", "feriado", String.valueOf(id), null);
		return FeriadoDto.de(f);
	}

	@Transactional
	public void desativar(Long atorId, Long id) {
		feriados.findById(id).orElseThrow(() -> new CalendarioException("Feriado nao encontrado")).desativar();
		auditoria.registrar(atorId, "FERIADO_DESATIVADO", "feriado", String.valueOf(id), null);
	}

	/** Datas nao uteis (feriados e suspensoes) aplicaveis ao contexto no intervalo; fins de semana ficam com {@link DiasUteis}. */
	@Transactional(readOnly = true)
	public Set<LocalDate> naoUteis(ContextoCalendario ctx, LocalDate de, LocalDate ate) {
		if (!feriados.existeNacionalNoPeriodo(de, ate)) {
			throw new CalendarioException("Calendario nao cadastrado para o periodo " + de + " a " + ate
					+ "; cadastre os feriados antes de calcular prazos");
		}
		Set<LocalDate> datas = new HashSet<>();
		for (Feriado f : feriados.findAtivosNoPeriodo(de, ate)) {
			if (aplica(f, ctx)) {
				for (LocalDate d = f.getDataInicial(); !d.isAfter(f.getDataFinal()); d = d.plusDays(1)) {
					datas.add(d);
				}
			}
		}
		return datas;
	}

	/** Prazo interno = fatal menos {@code n} dias uteis no calendario do contexto. */
	@Transactional(readOnly = true)
	public LocalDate subtrairDiasUteis(ContextoCalendario ctx, LocalDate fatal, int n) {
		// janela folgada: 3 dias uteis nunca passam de 60 dias corridos, mesmo com recesso de 32 dias
		Set<LocalDate> naoUteis = naoUteis(ctx, fatal.minusDays(60), fatal);
		return DiasUteis.subtrair(fatal, n, naoUteis);
	}

	static boolean aplica(Feriado f, ContextoCalendario ctx) {
		return switch (f.getEscopo()) {
			case NACIONAL -> true;
			case TRIBUNAL -> igual(f.getReferencia(), ctx.tribunal());
			case ESTADO -> igual(f.getReferencia(), ctx.uf());
			case MUNICIPIO -> igual(f.getReferencia(), ctx.municipio());
		};
	}

	private static boolean igual(String a, String b) {
		return a != null && b != null && a.trim().toUpperCase(Locale.ROOT).equals(b.trim().toUpperCase(Locale.ROOT));
	}

	private static String ref(EscopoFeriado escopo, String referencia) {
		if (escopo == EscopoFeriado.NACIONAL) {
			return null;
		}
		if (referencia == null || referencia.isBlank()) {
			throw new CalendarioException("Informe a referencia (tribunal, UF ou municipio)");
		}
		return referencia.trim().toUpperCase(Locale.ROOT);
	}

	private static LocalDate validarFim(LocalDate inicial, LocalDate fim) {
		LocalDate f = fim == null ? inicial : fim;
		if (f.isBefore(inicial)) {
			throw new CalendarioException("A data final nao pode ser anterior a inicial");
		}
		return f;
	}
}
