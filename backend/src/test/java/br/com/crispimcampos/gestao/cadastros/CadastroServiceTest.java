package br.com.crispimcampos.gestao.cadastros;

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
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CadastroServiceTest {

	static final long LETICIA = 1, ANDRESSA = 3, WENDELL = 4, LUANA = 5;
	static final String CNJ = "00020802520125150049";

	ClienteRepository clientes = mock(ClienteRepository.class);
	ProcessoRepository processos = mock(ProcessoRepository.class);
	ParteProcessoRepository partes = mock(ParteProcessoRepository.class);
	Visibilidade visibilidade = mock(Visibilidade.class);
	UsuarioService usuarios = mock(UsuarioService.class);
	CadastroService servico = new CadastroService(clientes, processos, partes, visibilidade, usuarios,
			mock(AuditoriaService.class));

	UsuarioLogado luana = ator(LUANA, Perfil.ASSISTENTE);
	UsuarioLogado wendell = ator(WENDELL, Perfil.ADVOGADO);
	UsuarioLogado leticia = ator(LETICIA, Perfil.SOCIA);

	static UsuarioLogado ator(long id, Perfil p) {
		return new UsuarioLogado(id, "Teste " + id, "u" + id + "@exemplo.test", "x", p, true);
	}

	static <T> T comId(T o, long id) {
		ReflectionTestUtils.setField(o, "id", id);
		return o;
	}

	Cliente cliente(long id) {
		Cliente c = comId(new Cliente("Cliente Ficticio", null, null, null, null, null), id);
		when(clientes.findById(id)).thenReturn(Optional.of(c));
		return c;
	}

	Processo processo(long id, Cliente c, long titular) {
		Processo p = comId(new Processo(CNJ, c, titular), id);
		p.atualizar("TJDFT", null, null, null, null, c, titular);
		when(processos.findById(id)).thenReturn(Optional.of(p));
		return p;
	}

	@BeforeEach
	void prepara() {
		when(visibilidade.podeVer(any(), any(), any())).thenReturn(false);
		when(visibilidade.titularesVisiveis(any(), any())).thenReturn(Set.of());
		when(partes.findByProcessoIdOrderByIdAsc(any())).thenReturn(List.of());
		when(processos.save(any())).thenAnswer(i -> comId(i.getArgument(0), 50L));
		when(clientes.save(any())).thenAnswer(i -> comId(i.getArgument(0), 60L));
	}

	@Test
	void processoForaDaVisibilidadeParecInexistente() {
		processo(10, cliente(1), WENDELL);
		when(visibilidade.podeVer(luana, Escopo.PROCESSO, WENDELL)).thenReturn(false);
		assertThatThrownBy(() -> servico.buscarProcesso(luana, 10L)).isInstanceOf(CadastroNaoEncontradoException.class);
	}

	@Test
	void processoVisivelEhRetornadoComNumeroFormatado() {
		processo(10, cliente(1), ANDRESSA);
		when(visibilidade.podeVer(luana, Escopo.PROCESSO, ANDRESSA)).thenReturn(true);
		var dto = servico.buscarProcesso(luana, 10L);
		assertThat(dto.numeroFormatado()).isEqualTo("0002080-25.2012.5.15.0049");
	}

	@Test
	void listagemConsultaApenasTitularesVisiveis() {
		when(visibilidade.titularesVisiveis(luana, Escopo.PROCESSO)).thenReturn(Set.of((long) LETICIA, ANDRESSA));
		when(processos.findByAdvogadoIdInOrderByIdDesc(Set.of((long) LETICIA, ANDRESSA))).thenReturn(List.of());
		assertThat(servico.listarProcessos(luana, null)).isEmpty();
		verify(processos).findByAdvogadoIdInOrderByIdDesc(Set.of((long) LETICIA, ANDRESSA));
	}

	@Test
	void semTitularesNaoConsultaOBanco() {
		assertThat(servico.listarProcessos(wendell, null)).isEmpty();
		assertThat(servico.listarClientes(wendell)).isEmpty();
		verify(processos, never()).findByAdvogadoIdInOrderByIdDesc(any());
		verify(clientes, never()).findComProcessoDe(any());
	}

	@Test
	void sociaListaTodosOsClientes() {
		Cliente c = cliente(1);
		when(clientes.findAll()).thenReturn(List.of(c));
		assertThat(servico.listarClientes(leticia)).hasSize(1);
	}

	@Test
	void clienteSoEVisivelPorProcessoVisivel() {
		Cliente c = cliente(1);
		assertThatThrownBy(() -> servico.buscarCliente(wendell, 1L)).isInstanceOf(CadastroNaoEncontradoException.class);
		when(visibilidade.titularesVisiveis(wendell, Escopo.PROCESSO)).thenReturn(Set.of((long) WENDELL));
		Processo p = processo(10, c, WENDELL);
		when(processos.findByClienteIdAndAdvogadoIdInOrderByIdDesc(1L, Set.of((long) WENDELL))).thenReturn(List.of(p));
		assertThat(servico.buscarCliente(wendell, 1L).id()).isEqualTo(1L);
	}

	@Test
	void criaProcessoValidandoCnjDuplicidadeEResponsavel() {
		cliente(1);
		assertThatThrownBy(() -> servico.criarProcesso(1L, "123", "TJDFT", null, null, null, null, 1L, ANDRESSA))
				.isInstanceOf(RegraDeCadastroException.class);
		when(processos.existsByNumero(CNJ)).thenReturn(true);
		assertThatThrownBy(() -> servico.criarProcesso(1L, "0002080-25.2012.5.15.0049", "TJDFT", null, null, null, null, 1L, ANDRESSA))
				.isInstanceOf(RegraDeCadastroException.class).extracting("conflito").isEqualTo(true);
		when(processos.existsByNumero(CNJ)).thenReturn(false);
		when(usuarios.buscar((long) LUANA)).thenReturn(new UsuarioDto(5L, "L", "l@exemplo.test", Perfil.ASSISTENTE, true, null, List.of()));
		assertThatThrownBy(() -> servico.criarProcesso(1L, CNJ, "TJDFT", null, null, null, null, 1L, LUANA))
				.as("assistente nao pode ser responsavel").isInstanceOf(RegraDeCadastroException.class);
		when(usuarios.buscar((long) ANDRESSA)).thenReturn(new UsuarioDto(3L, "A", "a@exemplo.test", Perfil.ADVOGADO, true, null, List.of()));
		var dto = servico.criarProcesso(1L, CNJ, "TJDFT", null, null, null, new BigDecimal("1000.00"), 1L, ANDRESSA);
		assertThat(dto.advogadoId()).isEqualTo(3L);
		verify(partes).save(any());
	}

	@Test
	void clienteRecusaDocumentoInvalidoEDuplicado() {
		assertThatThrownBy(() -> servico.criarCliente(1L, "X", "111.111.111-11", null, null, null, null))
				.isInstanceOf(RegraDeCadastroException.class);
		Cliente existente = cliente(2);
		when(clientes.findByDocumento("52998224725")).thenReturn(Optional.of(existente));
		assertThatThrownBy(() -> servico.criarCliente(1L, "X", "529.982.247-25", null, null, null, null))
				.isInstanceOf(RegraDeCadastroException.class).extracting("conflito").isEqualTo(true);
		assertThat(servico.atualizarCliente(1L, 2L, "Novo Nome", "529.982.247-25", null, null, null, null).nome())
				.as("o proprio cliente pode manter o documento").isEqualTo("Novo Nome");
	}

	@Test
	void documentoEhGuardadoSoComDigitos() {
		var dto = servico.criarCliente(1L, " Maria Ficticia ", "529.982.247-25", "", "", null, null);
		assertThat(dto.documento()).isEqualTo("52998224725");
		assertThat(dto.nome()).isEqualTo("Maria Ficticia");
		assertThat(dto.email()).isNull();
	}
}
