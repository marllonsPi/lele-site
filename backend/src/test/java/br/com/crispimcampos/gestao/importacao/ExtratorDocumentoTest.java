package br.com.crispimcampos.gestao.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.crispimcampos.gestao.cadastros.NumeroCnj;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExtratorDocumentoTest {

	static final String CNJ = "0000123-" + NumeroCnj.calcularDv("0000123", "20258070001") + ".2025.8.07.0001";

	static final String CONTRATO = """
			CONTRATO DE HONORÁRIOS ADVOCATÍCIOS
			CONTRATANTE: MARIA EXEMPLO DA SILVA, brasileira, divorciada, portadora do RG nº 1.234.567 SSP/DF, inscrita no CPF sob o nº 529.982.247-25,
			residente e domiciliada na Quadra 10 Conjunto B Casa 5, Samambaia/DF, CEP 72300-000, e-mail maria.exemplo@exemplo.test, telefone (61) 99999-1234.
			CLÁUSULA 1ª - Os honorários contratuais são fixados em R$ 6.000,00 (seis mil reais), pagos com entrada de R$ 1.200,00 e 24 (vinte e quatro) parcelas mensais de R$ 200,00.
			CLÁUSULA 2ª - A ação tramita sob o número %s.
			Brasília, 15 de março de 2026.
			""".formatted(CNJ);

	@Test
	void extraiDadosCadastraisFinanceirosEProcessoDoContrato() {
		var d = ExtratorDocumento.extrair(CONTRATO);
		assertThat(d.documento()).isEqualTo("52998224725");
		assertThat(d.rg()).startsWith("1.234.567");
		assertThat(d.estadoCivil()).isEqualTo("divorciada");
		assertThat(d.endereco()).contains("Quadra 10");
		assertThat(d.email()).isEqualTo("maria.exemplo@exemplo.test");
		assertThat(d.telefone()).contains("99999-1234");
		assertThat(d.valorTotal()).isEqualByComparingTo("6000.00");
		assertThat(d.entrada()).isEqualByComparingTo("1200.00");
		assertThat(d.parcelas()).isEqualTo(24);
		assertThat(d.valorParcela()).isEqualByComparingTo("200.00");
		assertThat(d.dataAssinatura()).isEqualTo(LocalDate.of(2026, 3, 15));
		assertThat(d.processos()).containsExactly(CNJ.replaceAll("\\D", ""));
	}

	@Test
	void ignoraCpfInvalidoECnjComDigitoErrado() {
		var d = ExtratorDocumento.extrair("CPF 111.111.111-11 e processo 0000123-00.2025.8.07.0001");
		assertThat(d.documento()).isNull();
		assertThat(d.processos()).isEmpty();
	}

	@Test
	void cnpjValidoETextoVazio() {
		assertThat(ExtratorDocumento.extrair("empresa CNPJ 11.222.333/0001-81").documento()).isEqualTo("11222333000181");
		var vazio = ExtratorDocumento.extrair("  ");
		assertThat(vazio.documento()).isNull();
		assertThat(vazio.processos()).isEmpty();
	}

	@Test
	void leTextoDeDocxETxtEIgnoraOutrosFormatos() throws Exception {
		var out = new java.io.ByteArrayOutputStream();
		try (var z = new java.util.zip.ZipOutputStream(out)) {
			z.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
			z.write("<w:document><w:p><w:r><w:t>CPF 529.982.247-25</w:t></w:r></w:p></w:document>".getBytes());
			z.closeEntry();
		}
		assertThat(TextoDeArquivo.ler("c.docx", out.toByteArray())).contains("529.982.247-25");
		assertThat(TextoDeArquivo.ler("c.txt", "texto".getBytes())).isEqualTo("texto");
		assertThat(TextoDeArquivo.ler("foto.jpg", new byte[] {1})).isNull();
		assertThat(TextoDeArquivo.ler("quebrado.pdf", "nao e pdf".getBytes())).isNull();
	}
}
