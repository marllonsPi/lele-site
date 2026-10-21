package br.com.crispimcampos.gestao.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.crispimcampos.gestao.drive.DriveEscritorio;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/** O Drive do escritorio como armazenamento: prefere o Drive, cai no disco se estiver inativo ou falhar, le de volta pelo id. */
class ArmazenamentoDriveTest {

	@TempDir
	Path pasta;

	ArmazenamentoAnexos armazenamento(DriveEscritorio drive) {
		var a = new ArmazenamentoAnexos(pasta.toString());
		ReflectionTestUtils.invokeMethod(a, "ligarDrive", drive);
		return a;
	}

	@Test
	void comDriveAtivoOArquivoVaiParaOPastaDoClienteELeVoltaPeloId() throws IOException {
		var drive = mock(DriveEscritorio.class);
		when(drive.ativo()).thenReturn(true);
		when(drive.enviar(eq(List.of("Maria Cliente", "0000001-31.2025.8.07.0001")), eq("peticao.pdf"), any(), any())).thenReturn("ID_DRIVE_12345");
		when(drive.baixar("ID_DRIVE_12345")).thenReturn("conteudo".getBytes());
		var arm = armazenamento(drive);

		String caminho = arm.salvarPreferindoDrive(List.of("Maria Cliente", "0000001-31.2025.8.07.0001"), "peticao.pdf", null, "conteudo".getBytes());

		assertThat(caminho).isEqualTo("drive:ID_DRIVE_12345");
		assertThat(ArmazenamentoAnexos.noDrive(caminho)).isTrue();
		assertThat(arm.abrir(caminho).getContentAsByteArray()).isEqualTo("conteudo".getBytes());
	}

	@Test
	void semDriveAtivoOuSeOEnvioFalharFicaNoDiscoLocal() throws IOException {
		var inativo = mock(DriveEscritorio.class);
		when(inativo.ativo()).thenReturn(false);
		String local = armazenamento(inativo).salvarPreferindoDrive(List.of("X"), "a.pdf", null, "abc".getBytes());
		assertThat(ArmazenamentoAnexos.noDrive(local)).isFalse();

		var quebrado = mock(DriveEscritorio.class);
		when(quebrado.ativo()).thenReturn(true);
		when(quebrado.enviar(any(), any(), any(), any())).thenThrow(new IllegalStateException("Drive fora do ar"));
		var arm = armazenamento(quebrado);
		String c = arm.salvarPreferindoDrive(List.of("X"), "a.pdf", null, "abc".getBytes());
		assertThat(ArmazenamentoAnexos.noDrive(c)).isFalse();
		assertThat(arm.abrir(c).getContentAsByteArray()).isEqualTo("abc".getBytes());
	}

	@Test
	void caminhoDeDriveInvalidoNaoEAceito() {
		var arm = armazenamento(mock(DriveEscritorio.class));
		assertThatThrownBy(() -> arm.abrir("drive:../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
	}
}
