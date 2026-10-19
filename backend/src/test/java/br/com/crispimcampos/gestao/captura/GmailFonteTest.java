package br.com.crispimcampos.gestao.captura;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.crispimcampos.gestao.cadastros.NumeroCnj;
import br.com.crispimcampos.gestao.google.MensagemGmail;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GmailFonteTest {

	/** Numero CNJ ficticio com digito verificador calculado, nunca de processo real. */
	static String cnjFicticio() {
		return NumeroCnj.formatar("0000001" + NumeroCnj.calcularDv("0000001", "20269990001") + "20269990001");
	}

	@Test
	void convertePreservaIdDataEAchaNumeroCnjValido() {
		String cnj = cnjFicticio();
		var d = GmailFonte.converter(new MensagemGmail("abc", "Intimacao " + cnj, "x@tribunal.jus.br", LocalDate.of(2026, 10, 15), "corpo ficticio"));
		assertThat(d.fonte()).isEqualTo(Fonte.EMAIL);
		assertThat(d.idExterno()).isEqualTo("abc");
		assertThat(d.numeroProcesso()).isEqualTo(cnj);
		assertThat(d.dataPublicacao()).isEqualTo(LocalDate.of(2026, 10, 15));
		assertThat(d.conteudo()).contains("corpo ficticio");
		assertThat(d.hash()).hasSize(64);
	}

	@Test
	void numeroComDigitoInvalidoEIgnoradoEEntraPorHash() {
		var d = GmailFonte.converter(new MensagemGmail("z", "0000001-00.2026.9.99.0001", "x", LocalDate.of(2026, 10, 15), "sem numero"));
		assertThat(d.numeroProcesso()).isNull();
		assertThat(PipelineCaptura.chave(d)).startsWith("SN-");
	}

	@Test
	void textoMuitoLongoETruncado() {
		var d = GmailFonte.converter(new MensagemGmail("z", "a", "x", LocalDate.of(2026, 10, 15), "a".repeat(50_000)));
		assertThat(d.conteudo()).hasSize(GmailFonte.MAXIMO_TEXTO);
	}
}
