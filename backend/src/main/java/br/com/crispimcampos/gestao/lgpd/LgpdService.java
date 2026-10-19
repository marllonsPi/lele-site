package br.com.crispimcampos.gestao.lgpd;

import br.com.crispimcampos.gestao.atendimento.AtendimentoService;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.relatorios.RelatorioClienteService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Direitos do titular (LGPD): exportar os dados de um cliente e anonimiza-los. Exclusivo das socias e sempre auditado.
 * A anonimizacao preserva o que a lei manda reter (processos, relatorios enviados, auditoria) e remove dado pessoal livre.
 */
@Service
public class LgpdService {

	private final CadastroService cadastros;
	private final AtendimentoService atendimentos;
	private final RelatorioClienteService relatorios;
	private final AuditoriaService auditoria;

	LgpdService(CadastroService cadastros, AtendimentoService atendimentos, RelatorioClienteService relatorios, AuditoriaService auditoria) {
		this.cadastros = cadastros;
		this.atendimentos = atendimentos;
		this.relatorios = relatorios;
		this.auditoria = auditoria;
	}

	@Transactional
	public Map<String, Object> exportar(UsuarioLogado ator, Long clienteId) {
		exigirSocia(ator);
		Map<String, Object> dados = new LinkedHashMap<>();
		dados.put("geradoEm", Instant.now().toString());
		dados.put("cliente", cadastros.buscarCliente(ator, clienteId));
		dados.put("processos", cadastros.listarProcessos(ator, clienteId));
		dados.put("atendimentos", atendimentos.listar(ator, clienteId));
		dados.put("relatoriosEnviados", relatorios.doCliente(ator, clienteId).stream()
				.map(r -> Map.of("id", r.id(), "estado", r.estado(), "periodoFim", String.valueOf(r.periodoFim()),
						"enviadoEm", String.valueOf(r.enviadoEm()), "canal", String.valueOf(r.canal())))
				.toList());
		auditoria.registrar(ator.id(), "LGPD_EXPORTACAO", "cliente", String.valueOf(clienteId), null);
		return dados;
	}

	@Transactional
	public void anonimizar(UsuarioLogado ator, Long clienteId) {
		exigirSocia(ator);
		cadastros.anonimizarCliente(ator.id(), clienteId);
		atendimentos.anonimizarResumos(clienteId);
		auditoria.registrar(ator.id(), "LGPD_ANONIMIZACAO", "cliente", String.valueOf(clienteId), null);
	}

	private static void exigirSocia(UsuarioLogado u) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("Direitos do titular sao tratados apenas pelas socias");
		}
	}

	/** Resumo do que o sistema guarda, para a politica de privacidade e o atendimento ao titular. */
	public static List<String> categoriasDeDados() {
		return List.of("Cadastro do cliente (nome, CPF/CNPJ, contatos, endereco, observacoes)", "Processos e partes",
				"Atendimentos e andamentos", "Relatorios enviados ao cliente", "Financeiro (somente socias)");
	}
}
