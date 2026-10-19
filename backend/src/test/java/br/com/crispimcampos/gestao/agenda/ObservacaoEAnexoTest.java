package br.com.crispimcampos.gestao.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroNaoEncontradoException;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class ObservacaoEAnexoTest {

	@TempDir
	Path pasta;

	AgendamentoRepository repo = mock(AgendamentoRepository.class);
	Visibilidade visibilidade = mock(Visibilidade.class);
	RegrasAgendamento regras = new RegrasAgendamento(repo, visibilidade, mock(UsuarioService.class));
	AuditoriaService auditoria = mock(AuditoriaService.class);
	ObservacaoRepository obsRepo = mock(ObservacaoRepository.class);
	ObservacaoService observacoes = new ObservacaoService(obsRepo, regras, auditoria);
	AnexoRepository anexoRepo = mock(AnexoRepository.class);
	CadastroService cadastros = mock(CadastroService.class);

	UsuarioLogado leticia = new UsuarioLogado(1L, "L", "l@exemplo.test", "x", Perfil.SOCIA, true);
	UsuarioLogado wendell = new UsuarioLogado(4L, "W", "w@exemplo.test", "x", Perfil.ADVOGADO, true);

	AnexoService anexos() {
		return new AnexoService(anexoRepo, new ArmazenamentoAnexos(pasta.toString()), regras, cadastros, auditoria);
	}

	void agendamentoDeAndressaVisivelParaLeticia() {
		Agendamento a = Agendamento.lancarPrazo(10L, 3L, TipoAgendamento.CON, null, 3L, 1L, false,
				LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-14"));
		ReflectionTestUtils.setField(a, "id", 50L);
		when(repo.findById(50L)).thenReturn(Optional.of(a));
		when(visibilidade.podeVer(leticia, Escopo.AGENDAMENTO, 3L)).thenReturn(true);
	}

	// ---- observacoes

	@Test
	void observacaoRegistraAutorETextoESoParaQuemVe() {
		agendamentoDeAndressaVisivelParaLeticia();
		when(obsRepo.save(any())).thenAnswer(i -> i.getArgument(0));
		var dto = observacoes.adicionar(leticia, 50L, "  conferir o protocolo  ");
		assertThat(dto.autorId()).isEqualTo(1L);
		assertThat(dto.texto()).isEqualTo("conferir o protocolo");
		assertThatThrownBy(() -> observacoes.adicionar(wendell, 50L, "x")).isInstanceOf(AgendaNaoEncontradaException.class);
		assertThatThrownBy(() -> observacoes.listar(wendell, 50L)).isInstanceOf(AgendaNaoEncontradaException.class);
	}

	@Test
	void observacaoVaziaOuGrandeDemaisERecusada() {
		agendamentoDeAndressaVisivelParaLeticia();
		assertThatThrownBy(() -> observacoes.adicionar(leticia, 50L, "   ")).isInstanceOf(RegraDeAgendaException.class);
		assertThatThrownBy(() -> observacoes.adicionar(leticia, 50L, "a".repeat(ObservacaoService.TEXTO_MAXIMO + 1)))
				.isInstanceOf(RegraDeAgendaException.class);
		verify(obsRepo, never()).save(any());
	}

	// ---- anexos

	static byte[] pdf() {
		return "%PDF-1.4 conteudo ficticio".getBytes();
	}

	@Test
	void anexaPdfAoAgendamentoGravandoComNomeOpaco() {
		agendamentoDeAndressaVisivelParaLeticia();
		when(anexoRepo.save(any())).thenAnswer(i -> i.getArgument(0));
		var dto = anexos().anexarAoAgendamento(leticia, 50L, "C:\\fakepath\\../peca final.pdf", pdf());
		assertThat(dto.nome()).isEqualTo("peca final.pdf");
		assertThat(pasta.toFile().list()).hasSize(1).allMatch(n -> n.matches("[0-9a-f-]{36}"));
	}

	@Test
	void recusaTipoExtensaoOuAssinaturaIncoerente() {
		agendamentoDeAndressaVisivelParaLeticia();
		AnexoService s = anexos();
		assertThatThrownBy(() -> s.anexarAoAgendamento(leticia, 50L, "x.exe", pdf())).isInstanceOf(RegraDeAgendaException.class);
		assertThatThrownBy(() -> s.anexarAoAgendamento(leticia, 50L, "x.pdf", "MZ executavel".getBytes())).isInstanceOf(RegraDeAgendaException.class);
		assertThatThrownBy(() -> s.anexarAoAgendamento(leticia, 50L, "x.html", "<script>".getBytes())).isInstanceOf(RegraDeAgendaException.class);
		assertThatThrownBy(() -> s.anexarAoAgendamento(leticia, 50L, "x.pdf", new byte[0])).isInstanceOf(RegraDeAgendaException.class);
		assertThat(pasta.toFile().list()).isEmpty();
	}

	@Test
	void recusaArquivoMaiorQue20Mb() {
		agendamentoDeAndressaVisivelParaLeticia();
		byte[] grande = new byte[(int) AnexoService.TAMANHO_MAXIMO + 1];
		System.arraycopy(pdf(), 0, grande, 0, 4);
		assertThatThrownBy(() -> anexos().anexarAoAgendamento(leticia, 50L, "x.pdf", grande)).isInstanceOf(RegraDeAgendaException.class);
	}

	@Test
	void anexoDeProcessoSeguePermissaoDoProcesso() {
		when(cadastros.buscarProcesso(wendell, 10L)).thenThrow(new CadastroNaoEncontradoException("Processo nao encontrado"));
		assertThatThrownBy(() -> anexos().anexarAoProcesso(wendell, 10L, "x.pdf", pdf())).isInstanceOf(CadastroNaoEncontradoException.class);
		verify(anexoRepo, never()).save(any());
	}

	@Test
	void downloadExigeVisibilidadeDoDono() {
		agendamentoDeAndressaVisivelParaLeticia();
		when(anexoRepo.save(any())).thenAnswer(i -> {
			Object o = i.getArgument(0);
			ReflectionTestUtils.setField(o, "id", 9L);
			return o;
		});
		AnexoService s = anexos();
		s.anexarAoAgendamento(leticia, 50L, "peca.pdf", pdf());
		var salvo = org.mockito.ArgumentCaptor.forClass(Anexo.class);
		verify(anexoRepo).save(salvo.capture());
		when(anexoRepo.findById(9L)).thenReturn(Optional.of(salvo.getValue()));
		var d = s.baixar(leticia, 9L);
		assertThat(d.nome()).isEqualTo("peca.pdf");
		assertThat(d.arquivo().exists()).isTrue();
		assertThatThrownBy(() -> s.baixar(wendell, 9L)).isInstanceOf(AgendaNaoEncontradaException.class);
	}

	@Test
	void armazenamentoNaoAbreCaminhoForaDoPadrao() {
		var arm = new ArmazenamentoAnexos(pasta.toString());
		assertThatThrownBy(() -> arm.abrir("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nomeLimpoRemoveDiretoriosECaracteresDeControle() {
		assertThat(AnexoService.nomeLimpo("../../a\u0000b.pdf")).isEqualTo("ab.pdf");
		assertThatThrownBy(() -> AnexoService.nomeLimpo("   ")).isInstanceOf(RegraDeAgendaException.class);
	}
}
