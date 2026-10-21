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
		return listarClientes(usuario, null);
	}

	/** {@code area} opcional: so clientes que atuam nessa area. */
	@Transactional(readOnly = true)
	public List<ClienteDto> listarClientes(UsuarioLogado usuario, AreaAtuacao area) {
		List<Cliente> base;
		if (usuario.perfil() == Perfil.SOCIA) {
			base = clientes.findAll();
		} else {
			Set<Long> titulares = visibilidade.titularesVisiveis(usuario, Escopo.PROCESSO);
			if (titulares.isEmpty()) {
				return List.of();
			}
			base = clientes.findComProcessoDe(titulares);
		}
		return base.stream().filter(c -> area == null || c.getAreas().contains(area)).map(ClienteDto::de).toList();
	}

	@Transactional(readOnly = true)
	public ClienteDto buscarCliente(UsuarioLogado usuario, Long id) {
		Cliente c = obterCliente(id);
		if (usuario.perfil() != Perfil.SOCIA && !temProcessoVisivel(usuario, id)) {
			throw new CadastroNaoEncontradoException("Cliente nao encontrado");
		}
		return ClienteDto.de(c);
	}

	/** Area de atuacao obrigatoria (uma ou mais). */
	@Transactional
	public ClienteDto criarCliente(Long atorId, String nome, String documento, String email, String telefone,
			String endereco, String observacoes, Set<AreaAtuacao> areas) {
		exigirAreas(areas);
		return salvarNovoCliente(atorId, nome, documento, email, telefone, endereco, observacoes, areas);
	}

	/** Cliente vindo de importacao: pode nascer sem documento, marcado "a conferir" e com a origem (PLANILHA, DRIVE). */
	@Transactional
	public ClienteDto criarClienteImportado(Long atorId, String nome, String documento, String email, String telefone,
			String endereco, String observacoes, Set<AreaAtuacao> areas, boolean aConferir, String origem) {
		exigirAreas(areas);
		ClienteDto dto = salvarNovoCliente(atorId, nome, documento, email, telefone, endereco, observacoes, areas);
		Cliente c = obterCliente(dto.id());
		c.definirImportacao(aConferir, origem);
		return ClienteDto.de(c);
	}

	/** Soma areas ao cliente sem remover as existentes (importacao nunca reduz o cadastro). */
	@Transactional
	public void adicionarAreas(Long atorId, Long clienteId, Set<AreaAtuacao> novas) {
		Cliente c = obterCliente(clienteId);
		Set<AreaAtuacao> todas = java.util.EnumSet.noneOf(AreaAtuacao.class);
		todas.addAll(c.getAreas());
		todas.addAll(novas);
		c.definirAreas(todas);
		auditoria.registrar(atorId, "CLIENTE_AREAS_ADICIONADAS", "cliente", String.valueOf(clienteId), null);
	}

	/** Dados cadastrais complementares (RG, estado civil) e demais campos vindos do Drive; so preenche o que esta vazio. */
	@Transactional
	public void completarDadosCadastrais(Long atorId, Long clienteId, String rg, String estadoCivil, String email,
			String telefone, String endereco) {
		Cliente c = obterCliente(clienteId);
		c.atualizar(c.getNome(), c.getDocumento(), c.getEmail() != null ? c.getEmail() : vazioParaNulo(email),
				c.getTelefone() != null ? c.getTelefone() : vazioParaNulo(telefone),
				c.getEndereco() != null ? c.getEndereco() : vazioParaNulo(endereco), c.getObservacoes());
		c.definirDadosCadastrais(c.getRg() != null ? c.getRg() : vazioParaNulo(rg),
				c.getEstadoCivil() != null ? c.getEstadoCivil() : vazioParaNulo(estadoCivil));
		auditoria.registrar(atorId, "CLIENTE_DADOS_COMPLETADOS", "cliente", String.valueOf(clienteId), null);
	}

	/** Dados cadastrais vindos de importacao; campos nulos nao mexem em nada. */
	public record DadosImportados(String documento, String rg, String estadoCivil, String endereco, String telefone, String email) {
	}

	/**
	 * Aplica dados importados ao cliente: preenche o que esta vazio e, so com {@code sobrescrever}, troca o que ja existe
	 * (decisao explicita da socia na previa). O CPF/CNPJ segue a mesma validacao e unicidade do cadastro.
	 */
	@Transactional
	public void aplicarDadosImportados(Long atorId, Long clienteId, DadosImportados d, boolean sobrescrever) {
		Cliente c = obterCliente(clienteId);
		boolean troca = sobrescrever;
		String doc = d.documento() != null && (c.getDocumento() == null || troca) ? documentoNormalizado(d.documento(), clienteId) : c.getDocumento();
		c.atualizar(c.getNome(), doc, pega(c.getEmail(), d.email(), troca), pega(c.getTelefone(), d.telefone(), troca), pega(c.getEndereco(), d.endereco(), troca),
				c.getObservacoes());
		c.definirDadosCadastrais(pega(c.getRg(), d.rg(), troca), pega(c.getEstadoCivil(), d.estadoCivil(), troca));
		auditoria.registrar(atorId, "CLIENTE_DADOS_IMPORTADOS", "cliente", String.valueOf(clienteId), sobrescrever ? "sobrescrevendo" : "so campos vazios");
	}

	private static String pega(String atual, String novo, boolean sobrescrever) {
		String n = vazioParaNulo(novo);
		return n != null && (atual == null || atual.isBlank() || sobrescrever) ? n : atual;
	}

	/** A socia confirma que o cadastro importado foi conferido. */
	@Transactional
	public void confirmarCadastroImportado(Long atorId, Long clienteId) {
		obterCliente(clienteId).confirmarCadastro();
		auditoria.registrar(atorId, "CLIENTE_CADASTRO_CONFERIDO", "cliente", String.valueOf(clienteId), null);
	}

	private ClienteDto salvarNovoCliente(Long atorId, String nome, String documento, String email, String telefone,
			String endereco, String observacoes, Set<AreaAtuacao> areas) {
		String doc = documentoNormalizado(documento, null);
		Cliente c = new Cliente(nome.trim(), doc, vazioParaNulo(email), vazioParaNulo(telefone),
				vazioParaNulo(endereco), vazioParaNulo(observacoes));
		c.definirAreas(areas == null ? Set.of() : areas);
		c = clientes.save(c);
		auditoria.registrar(atorId, "CLIENTE_CRIADO", "cliente", String.valueOf(c.getId()), null);
		return ClienteDto.de(c);
	}

	@Transactional
	public ClienteDto atualizarCliente(Long atorId, Long id, String nome, String documento, String email,
			String telefone, String endereco, String observacoes, Set<AreaAtuacao> areas) {
		exigirAreas(areas);
		Cliente c = obterCliente(id);
		c.definirAreas(areas);
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
		return listarProcessos(usuario, clienteId, null);
	}

	/** {@code area} opcional: so processos dessa area. */
	@Transactional(readOnly = true)
	public List<ProcessoDto> listarProcessos(UsuarioLogado usuario, Long clienteId, AreaAtuacao area) {
		Set<Long> titulares = visibilidade.titularesVisiveis(usuario, Escopo.PROCESSO);
		if (titulares.isEmpty()) {
			return List.of();
		}
		List<Processo> lista = clienteId == null ? processos.findByAdvogadoIdInOrderByIdDesc(titulares)
				: processos.findByClienteIdAndAdvogadoIdInOrderByIdDesc(clienteId, titulares);
		return lista.stream().filter(p -> area == null || p.getArea() == area).map(this::paraDto).toList();
	}

	/** Tela do processo. Sem visibilidade, responde como inexistente. */
	@Transactional(readOnly = true)
	public ProcessoDto buscarProcesso(UsuarioLogado usuario, Long id) {
		return paraDto(obterProcessoVisivel(usuario, id));
	}

	@Transactional
	public ProcessoDto criarProcesso(Long atorId, String numero, String tribunal, String varaComarca, String classe,
			String situacao, BigDecimal valorCausa, Long clienteId, Long advogadoId, AreaAtuacao area) {
		if (area == null) {
			throw regra("Informe a area de atuacao do processo");
		}
		return salvarNovoProcesso(atorId, numero, tribunal, varaComarca, classe, situacao, valorCausa, clienteId,
				advogadoId, area);
	}

	private ProcessoDto salvarNovoProcesso(Long atorId, String numero, String tribunal, String varaComarca,
			String classe, String situacao, BigDecimal valorCausa, Long clienteId, Long advogadoId, AreaAtuacao area) {
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
		p.definirArea(area);
		p = processos.save(p);
		partes.save(new ParteProcesso(p.getId(), cliente.getNome(), PapelParte.CLIENTE));
		auditoria.registrar(atorId, "PROCESSO_CRIADO", "processo", String.valueOf(p.getId()), null);
		return paraDto(p);
	}

	/** Cadastro a partir de uma publicacao: reaproveita cliente de mesmo nome, evita duplicar partes. */
	@Transactional
	public ProcessoDto cadastrarProcessoDePublicacao(Long atorId, String numero, String tribunal, String varaComarca,
			String classe, Long clienteId, String nomeCliente, Long advogadoId, List<String> contrarias) {
		Long cliente = clienteId;
		if (cliente == null) {
			if (nomeCliente == null || nomeCliente.isBlank()) {
				throw regra("Escolha o cliente");
			}
			cliente = clientes.findFirstByNomeIgnoreCaseAndAtivoTrue(nomeCliente.trim()).map(Cliente::getId)
					.orElseGet(() -> salvarNovoCliente(atorId, nomeCliente, null, null, null, null,
							"Criado a partir de publicacao", Set.of()).id());
		}
		ProcessoDto p = salvarNovoProcesso(atorId, numero, tribunal == null || tribunal.isBlank() ? "N/I" : tribunal,
				varaComarca, classe, null, null, cliente, advogadoId, null);
		String nomeDoCliente = clientes.findById(cliente).map(Cliente::getNome).orElse("");
		for (String c : contrarias == null ? List.<String>of() : contrarias) {
			if (c != null && !c.isBlank() && !c.trim().equalsIgnoreCase(nomeDoCliente)) {
				adicionarParte(atorId, p.id(), c, PapelParte.CONTRARIA);
			}
		}
		return p;
	}

	@Transactional
	public ProcessoDto atualizarProcesso(Long atorId, Long id, String tribunal, String varaComarca, String classe,
			String situacao, BigDecimal valorCausa, Long clienteId, Long advogadoId, AreaAtuacao area) {
		if (area == null) {
			throw regra("Informe a area de atuacao do processo");
		}
		Processo p = obterProcesso(id);
		p.definirArea(area);
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

	/**
	 * LGPD: remove os dados pessoais do cliente e desativa o cadastro. Os processos (retencao legal) precisam estar desativados;
	 * a parte "cliente" deles deixa de carregar o nome.
	 */
	@Transactional
	public void anonimizarCliente(Long atorId, Long clienteId) {
		Cliente c = obterCliente(clienteId);
		if (processos.findAll().stream().anyMatch(p -> p.getCliente().getId().equals(clienteId) && p.isAtivo())) {
			throw new RegraDeCadastroException("Desative os processos do cliente antes de anonimizar", false);
		}
		c.atualizar("Cliente anonimizado #" + clienteId, null, null, null, null, null);
		c.desativar();
		for (Processo p : processos.findAll()) {
			if (p.getCliente().getId().equals(clienteId)) {
				partes.findByProcessoIdOrderByIdAsc(p.getId()).stream().filter(x -> x.getPapel() == PapelParte.CLIENTE)
						.forEach(x -> x.anonimizar("Cliente anonimizado #" + clienteId));
			}
		}
		auditoria.registrar(atorId, "CLIENTE_ANONIMIZADO", "cliente", String.valueOf(clienteId), null);
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

	/** Uso interno (painel, financeiro): area de cada processo informado; processos sem area ficam de fora. */
	@Transactional(readOnly = true)
	public java.util.Map<Long, AreaAtuacao> areasDosProcessos(java.util.Collection<Long> processoIds) {
		java.util.Map<Long, AreaAtuacao> m = new java.util.HashMap<>();
		for (Processo p : processos.findAllById(processoIds)) {
			if (p.getArea() != null) {
				m.put(p.getId(), p.getArea());
			}
		}
		return m;
	}

	/** Uso interno (financeiro, importacao): areas do cliente, sem expor outros dados. */
	@Transactional(readOnly = true)
	public Set<AreaAtuacao> areasDoCliente(Long clienteId) {
		return clientes.findById(clienteId).map(c -> Set.copyOf(c.getAreas())).orElse(Set.of());
	}

	// ---- internos

	private static void exigirAreas(Set<AreaAtuacao> areas) {
		if (areas == null || areas.isEmpty()) {
			throw regra("Informe ao menos uma area de atuacao");
		}
	}

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
