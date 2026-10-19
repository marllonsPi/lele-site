package br.com.crispimcampos.gestao.calendario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarioServiceTest {

	FeriadoRepository repo = mock(FeriadoRepository.class);
	CalendarioService servico = new CalendarioService(repo, mock(AuditoriaService.class));

	static LocalDate d(String s) {
		return LocalDate.parse(s);
	}

	static Feriado f(EscopoFeriado e, String ref, String ini, String fim) {
		return new Feriado(e, ref, TipoFeriado.FERIADO, d(ini), d(fim), "teste");
	}

	@Test
	void nacionalSempreAplicaELocalSoQuandoConfere() {
		var ctx = new ContextoCalendario("TJDFT", "DF", "BRASILIA");
		assertThat(CalendarioService.aplica(f(EscopoFeriado.NACIONAL, null, "2026-10-12", "2026-10-12"), ctx)).isTrue();
		assertThat(CalendarioService.aplica(f(EscopoFeriado.TRIBUNAL, "tjdft", "2026-10-12", "2026-10-12"), ctx)).isTrue();
		assertThat(CalendarioService.aplica(f(EscopoFeriado.TRIBUNAL, "TJSP", "2026-10-12", "2026-10-12"), ctx)).isFalse();
		assertThat(CalendarioService.aplica(f(EscopoFeriado.ESTADO, "DF", "2026-10-12", "2026-10-12"), ctx)).isTrue();
		assertThat(CalendarioService.aplica(f(EscopoFeriado.ESTADO, "SP", "2026-10-12", "2026-10-12"), ctx)).isFalse();
		assertThat(CalendarioService.aplica(f(EscopoFeriado.MUNICIPIO, "BRASILIA", "2026-10-12", "2026-10-12"), ctx)).isTrue();
	}

	@Test
	void contextoSemUfNaoAplicaFeriadoEstadual() {
		assertThat(CalendarioService.aplica(f(EscopoFeriado.ESTADO, "DF", "2026-10-12", "2026-10-12"),
				ContextoCalendario.deTribunal("TJDFT"))).isFalse();
	}

	@Test
	void recusaCalcularSemCalendarioCadastrado() {
		when(repo.existeNacionalNoPeriodo(any(), any())).thenReturn(false);
		assertThatThrownBy(() -> servico.subtrairDiasUteis(ContextoCalendario.deTribunal("TJDFT"), d("2031-05-10"), 3))
				.isInstanceOf(CalendarioException.class).hasMessageContaining("Calendario nao cadastrado");
	}

	@Test
	void subtraiConsiderandoFeriadoNacionalESuspensaoDoTribunal() {
		when(repo.existeNacionalNoPeriodo(any(), any())).thenReturn(true);
		when(repo.findAtivosNoPeriodo(any(), any())).thenReturn(List.of(
				f(EscopoFeriado.NACIONAL, null, "2026-10-12", "2026-10-12"),
				f(EscopoFeriado.TRIBUNAL, "TJDFT", "2026-10-08", "2026-10-08"),
				f(EscopoFeriado.TRIBUNAL, "TJSP", "2026-10-09", "2026-10-09")));
		// quarta 14/10: terca 13 (1), segunda 12 feriado, sexta 9 (2), quinta 8 suspensa no TJDFT, quarta 7 (3)
		assertThat(servico.subtrairDiasUteis(ContextoCalendario.deTribunal("tjdft"), d("2026-10-14"), 3))
				.isEqualTo(d("2026-10-07"));
		// em outro tribunal: a suspensao do TJDFT nao vale, a do TJSP (sexta 9) vale -> terca 13 (1), quinta 8 (2), quarta 7 (3)
		assertThat(servico.subtrairDiasUteis(ContextoCalendario.deTribunal("TJSP"), d("2026-10-14"), 3))
				.isEqualTo(d("2026-10-07"));
	}

	@Test
	void periodoInvalidoERecusado() {
		assertThatThrownBy(() -> servico.criar(1L, EscopoFeriado.NACIONAL, null, TipoFeriado.FERIADO, d("2026-05-02"), d("2026-05-01"), "x"))
				.isInstanceOf(CalendarioException.class);
		assertThatThrownBy(() -> servico.criar(1L, EscopoFeriado.TRIBUNAL, " ", TipoFeriado.FERIADO, d("2026-05-02"), null, "x"))
				.isInstanceOf(CalendarioException.class);
	}
}
