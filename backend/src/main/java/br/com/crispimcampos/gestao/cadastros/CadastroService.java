package br.com.crispimcampos.gestao.cadastros;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clientes e processos (item 4). Toda leitura de processo passa por {@link Visibilidade} (invariante 1);
 * cliente so e lido por quem enxerga ao menos um processo dele (socias veem todos).
 * Escrita: somente socias (quem mais pode criar fica em docs/05-pendencias.md, item 4).
 */
@Service
public class CadastroService {

	private final ClienteRepository clientes;
	private final ProcessoRepository processos;
	private final ParteProcessoRepository partes;
	private final Visibilidade visibilidade;
	private final UsuarioService usuarios;
	private final AuditoriaService auditoria;

	CadastroService(ClienteRepository clientes, ProcessoRepository processos, ParteProcessoRepository partes,
			Visibilidade visibilidade, UsuarioService usuarios, AuditoriaService auditoria) {
		this.clientes = clientes;
		this.processos = processos;
		this.partes = partes;
		this.visibilidade = visibilidade;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
	}

	// ---- clientes

	@Transactional(readOnly = true)
	public List<ClienteDto> listarClientes(UsuarioLogado usuario) {
		if (usuario.perfil() == Perfil.SOCIA) {
			return clientes.findAll().stream().map(ClienteDto::de).toList();
		}
		Set<Long> titulares = visibilidade.titularesVisiveis(usuario, Escopo.PROCESSO);
		if (titulares.isEmpty()) {
			return List.of();
		}
		return clientes.findComProcessoDe(titulares).stream().map(ClienteDto::de).toList();
	}

	@Transactional(readOnly = true)
	public ClienteDto buscarCliente(UsuarioLogado usuario, Long id) {
		Cliente c = obterCliente(id);
		if (usuario.perfil() != Perfil.SOCIA && !temProcessoVisivel(usuario, id)) {
			throw new CadastroNaoEncontradoException("Cliente nao encontrado");
		}
		return ClienteDto.de(c);
	}

	@Transactional
	public ClienteDto criarCliente(Long atorId, String nome, String documento, String email, String telefone,
			String endereco, String observacoes) {
		String doc = documentoNormalizado(documento, null);
		Cliente c = clientes.save(new Cliente(nome.trim(), doc, vazioParaNulo(email), vazioParaNulo(telefone),
				vazioParaNulo(endereco), vazioParaNulo(observacoes)));
		auditoria.registrar(atorId, "CLIENTE_CRIADO", "cliente", String.valueOf(c.getId()), null);
		return ClienteDto.de(c);
	}

	@Transactional
	public ClienteDto atualizarCliente(Long atorId, Long id, String nome, String documento, String email,
			String telefone, String endereco, String observacoes) {
		Cliente c = obterCliente(id);
		c.atualizar(nome.trim(), documentoNormalizado(documento, id), vazioParaNulo(email), vazioParaNulo(telefone),
				vazioParaNulo(endereco), vazioParaNulo(observacoes));
		auditoria.registrar(atorId, "CLIENTE_ATUALIZADO", "cliente", String.valueOf(id), null);
		return ClienteDto.de(c);
	}

	@Transactional
	public void desativarCliente(Long atorId, Long id) {
		obterCliente(id).desativar();
		auditoria.registrar(atorId, "CLIENTE_DESATIVADO", "cliente", String.valueOf(id), null);
	}

	@Transactional
	public void reativarCliente(Long atorId, Long id) {
		obterCliente(id).reativar();
		auditoria.registrar(atorId, "CLIENTE_REATIVADO", "cliente", String.valueOf(id), null);
	}

	// ---- processos

	@Transactional(readOnly = true)
	public List<ProcessoDto> listarProcessos(UsuarioLogado usuario, Long clienteId) {
		Set<Long> titulares = visibilidade.titularesVisiveis(usuario, Escopo.PROCESSO);
		if (titulares.isEmpty()) {
			return List.of();
		}
		List<Processo> lista = clienteId == null ? processos.findByAdvogadoIdInOrderByIdDesc(titulares)
				: processos.findByClienteIdAndAdvogadoIdInOrderByIdDesc(clienteId, titulares);
		return lista.stream().map(this::paraDto).toList();
	}

	/** Tela do processo. Sem visibilidade, responde como inexistente. */
	@Transactional(readOnly = true)
	public ProcessoDto buscarProcesso(UsuarioLogado usuario, Long id) {
		return paraDto(obterProcessoVisivel(usuario, id));
	}

	@Transactional
	public ProcessoDto criarProcesso(Long atorId, String numero, String tribunal, String varaComarca, String classe,
			String situacao, BigDecimal valorCausa, Long clienteId, Long advogadoId) {
		String n = NumeroCnj.normalizar(numero);
		if (!NumeroCnj.valido(n)) {
			throw regra("Numero de processo invalido (padrao CNJ)");
		}
		if (processos.existsByNumero(n)) {
			throw conflito("Este processo ja esta cadastrado");
		}
		Cliente cliente = clienteAtivo(clienteId);
		validarResponsavel(advogadoId);
		Processo p = new Processo(n, cliente, advogadoId);
		p.atualizar(tribunal.trim(), vazioParaNulo(varaComarca), vazioParaNulo(classe), vazioParaNulo(situacao),
				valorCausa, cliente, advogadoId);
		p = processos.save(p);
		partes.save(new ParteProcesso(p.getId(), cliente.getNome(), PapelParte.CLIENTE));
		auditoria.registrar(atorId, "PROCESSO_CRIADO", "processo", String.valueOf(p.getId()), null);
		return paraDto(p);
	}

	@Transactional
	public ProcessoDto atualizarProcesso(Long atorId, Long id, String tribunal, String varaComarca, String classe,
			String situacao, BigDecimal valorCausa, Long clienteId, Long advogadoId) {
		Processo p = obterProcesso(id);
		Cliente cliente = clienteId.equals(p.getCliente().getId()) ? p.getCliente() : clienteAtivo(clienteId);
		if (!advogadoId.equals(p.getAdvogadoId())) {
			validarResponsavel(advogadoId);
		}
		p.atualizar(tribunal.trim(), vazioParaNulo(varaComarca), vazioParaNulo(classe), vazioParaNulo(situacao),
				valorCausa, cliente, advogadoId);
		auditoria.registrar(atorId, "PROCESSO_ATUALIZADO", "processo", String.valueOf(id), null);
		return paraDto(p);
	}

	@Transactional
	public void desativarProcesso(Long atorId, Long id) {
		obterProcesso(id).desativar();
		auditoria.registrar(atorId, "PROCESSO_DESATIVADO", "processo", String.valueOf(id), null);
	}

	@Transactional
	public ProcessoDto adicionarParte(Long atorId, Long processoId, String nome, PapelParte papel) {
		Processo p = obterProcesso(processoId);
		partes.save(new ParteProcesso(processoId, nome.trim(), papel));
		auditoria.registrar(atorId, "PARTE_ADICIONADA", "processo", String.valueOf(processoId), "papel=" + papel);
		return paraDto(p);
	}

	/**
	 * Uso interno entre modulos (ex.: agenda): devolve titular e tribunal sem aplicar visibilidade.
	 * Quem chama e responsavel por checar a visibilidade do usuario sobre o titular no escopo do seu dado.
	 */
	@Transactional(readOnly = true)
	public ProcessoResumo resumo(Long processoId) {
		Processo p = obterProcesso(processoId);
		return new ProcessoResumo(p.getId(), p.getAdvogadoId(), p.getCliente().getId(), p.getTribunal(), p.isAtivo());
	}

	/** Cliente visivel ao usuario (socia: todos; demais: quem ve algum processo do cliente). */
	@Transactional(readOnly = true)
	public boolean clienteVisivel(UsuarioLogado usuario, Long clienteId) {
		try {
			buscarCliente(usuario, clienteId);
			return true;
		} catch (CadastroNaoEncontradoException e) {
			return false;
		}
	}

	/** Uso interno (alertas): numero CNJ formatado do processo, sem aplicar visibilidade nem expor outros dados. */
	@Transactional(readOnly = true)
	public String numeroFormatado(Long processoId) {
		return processos.findById(processoId).map(p -> NumeroCnj.formatar(p.getNumero())).orElse(null);
	}

	/** Uso interno (captura): processo cadastrado pelo numero CNJ, para vincular a publicacao automaticamente. */
	@Transactional(readOnly = true)
	public java.util.Optional<ProcessoResumo> processoPorNumero(String numero) {
		return processos.findByNumero(NumeroCnj.normalizar(numero))
				.map(p -> new ProcessoResumo(p.getId(), p.getAdvogadoId(), p.getCliente().getId(), p.getTribunal(), p.isAtivo()));
	}

	/** Uso interno entre modulos: o cliente existe e esta ativo? (nao revela dados). */
	@Transactional(readOnly = true)
	public boolean clienteExisteAtivo(Long clienteId) {
		return clientes.findById(clienteId).map(Cliente::isAtivo).orElse(false);
	}

	// ---- internos

	private boolean temProcessoVisivel(UsuarioLogado usuario, Long clienteId) {
		Set<Long> titulares = visibilidade.titularesVisiveis(usuario, Escopo.PROCESSO);
		return !titulares.isEmpty()
				&& !processos.findByClienteIdAndAdvogadoIdInOrderByIdDesc(clienteId, titulares).isEmpty();
	}

	private Processo obterProcessoVisivel(UsuarioLogado usuario, Long id) {
		Processo p = processos.findById(id).orElseThrow(() -> new CadastroNaoEncontradoException("Processo nao encontrado"));
		if (!visibilidade.podeVer(usuario, Escopo.PROCESSO, p.getAdvogadoId())) {
			throw new CadastroNaoEncontradoException("Processo nao encontrado");
		}
		return p;
	}

	private Processo obterProcesso(Long id) {
		return processos.findById(id).orElseThrow(() -> new CadastroNaoEncontradoException("Processo nao encontrado"));
	}

	private Cliente obterCliente(Long id) {
		return clientes.findById(id).orElseThrow(() -> new CadastroNaoEncontradoException("Cliente nao encontrado"));
	}

	private Cliente clienteAtivo(Long id) {
		Cliente c = obterCliente(id);
		if (!c.isAtivo()) {
			throw regra("Cliente desativado");
		}
		return c;
	}

	private void validarResponsavel(Long advogadoId) {
		UsuarioDto u;
		try {
			u = usuarios.buscar(advogadoId);
		} catch (RuntimeException e) {
			throw regra("Advogado responsavel nao encontrado");
		}
		if (!u.ativo() || (u.perfil() != Perfil.ADVOGADO && u.perfil() != Perfil.SOCIA)) {
			throw regra("O responsavel deve ser um advogado ou socia ativo");
		}
	}

	private String documentoNormalizado(String documento, Long idAtual) {
		if (documento == null || documento.isBlank()) {
			return null;
		}
		String d = Documento.normalizar(documento);
		if (!Documento.valido(d)) {
			throw regra("CPF/CNPJ invalido");
		}
		clientes.findByDocumento(d).filter(o -> !o.getId().equals(idAtual)).ifPresent(o -> {
			throw conflito("Ja existe cliente com este CPF/CNPJ");
		});
		return d;
	}

	private ProcessoDto paraDto(Processo p) {
		return ProcessoDto.de(p, partes.findByProcessoIdOrderByIdAsc(p.getId()));
	}

	private static String vazioParaNulo(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static RegraDeCadastroException regra(String m) {
		return new RegraDeCadastroException(m, false);
	}

	private static RegraDeCadastroException conflito(String m) {
		return new RegraDeCadastroException(m, true);
	}
}
