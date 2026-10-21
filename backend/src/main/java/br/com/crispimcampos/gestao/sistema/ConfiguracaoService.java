package br.com.crispimcampos.gestao.sistema;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuracao do escritorio guardada no banco (editavel na tela, sem mexer no sistema). So as chaves do catalogo
 * existem; o valor do banco vence, depois a propriedade {@code app.*} equivalente, depois o padrao.
 */
@Service
public class ConfiguracaoService {

	public record Campo(String chave, String rotulo, String grupo, String ajuda, int maximo, boolean longo) {
	}

	public static final String ESCRITORIO_NOME = "escritorio.nome";
	public static final String ESCRITORIO_ENDERECO = "escritorio.endereco";
	public static final String ESCRITORIO_CONTATO = "escritorio.contato";

	/** Catalogo: tudo que o sistema precisa saber do escritorio para cumprir suas tarefas (timbre dos PDFs, e-mails). */
	public static final List<Campo> CATALOGO = List.of(
			new Campo(ESCRITORIO_NOME, "Nome do escritório", "Escritório", "Aparece no timbre dos relatórios em PDF.", 120, false),
			new Campo("escritorio.cnpj", "CNPJ", "Escritório", "Opcional.", 20, false),
			new Campo(ESCRITORIO_ENDERECO, "Endereço", "Escritório", "Rua, número, cidade e CEP, como deve sair no timbre.", 300, true),
			new Campo(ESCRITORIO_CONTATO, "Telefone / contato", "Escritório", "Telefone ou WhatsApp principal.", 200, false),
			new Campo("escritorio.email", "E-mail do escritório", "Escritório", "Remetente e contato nos relatórios.", 254, false),
			new Campo("escritorio.site", "Site", "Escritório", "Endereço do site institucional.", 200, false),
			new Campo("relatorios.rodape", "Rodapé dos relatórios ao cliente", "Relatórios", "Frase final dos relatórios aprovados (ex.: aviso de sigilo).", 500, true));

	private final JdbcTemplate jdbc;
	private final Environment ambiente;
	private final AuditoriaService auditoria;

	ConfiguracaoService(JdbcTemplate jdbc, Environment ambiente, AuditoriaService auditoria) {
		this.jdbc = jdbc;
		this.ambiente = ambiente;
		this.auditoria = auditoria;
	}

	/** Valor efetivo da chave (banco, depois app.*, depois padrao). Nunca nulo. */
	@Transactional(readOnly = true)
	public String valor(String chave, String padrao) {
		List<String> v = jdbc.queryForList("select valor from configuracao where chave = ?", String.class, chave);
		if (!v.isEmpty()) {
			return v.get(0);
		}
		String propriedade = ambiente.getProperty("app." + chave);
		return propriedade != null && !propriedade.isBlank() ? propriedade : padrao;
	}

	/** Chave interna (interruptor), fora do catalogo da tela de configuracao. */
	@Transactional
	public void definirInterna(Long atorId, String chave, String valor) {
		jdbc.update("insert into configuracao (chave, valor, atualizado_em, atualizado_por) values (?, ?, now(), ?) "
				+ "on conflict (chave) do update set valor = excluded.valor, atualizado_em = now(), atualizado_por = excluded.atualizado_por",
				chave, valor, atorId);
		auditoria.registrar(atorId, "CONFIGURACAO_ALTERADA", "configuracao", chave, "valor=" + valor);
	}

	@Transactional(readOnly = true)
	public Map<String, String> todas() {
		Map<String, String> saida = new LinkedHashMap<>();
		for (Campo c : CATALOGO) {
			saida.put(c.chave(), valor(c.chave(), c.chave().equals(ESCRITORIO_NOME) ? "Crispim & Campos Advocacia" : ""));
		}
		return saida;
	}

	@Transactional
	public Map<String, String> salvar(Long atorId, Map<String, String> novos) {
		for (Map.Entry<String, String> e : novos.entrySet()) {
			Campo campo = CATALOGO.stream().filter(c -> c.chave().equals(e.getKey())).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("Configuracao desconhecida: " + e.getKey()));
			String valor = e.getValue() == null ? "" : e.getValue().trim();
			if (valor.length() > campo.maximo()) {
				throw new IllegalArgumentException(campo.rotulo() + " excede " + campo.maximo() + " caracteres");
			}
			if (campo.chave().equals(ESCRITORIO_NOME) && valor.isEmpty()) {
				throw new IllegalArgumentException("O nome do escritorio e obrigatorio");
			}
			jdbc.update("insert into configuracao (chave, valor, atualizado_em, atualizado_por) values (?, ?, now(), ?) "
					+ "on conflict (chave) do update set valor = excluded.valor, atualizado_em = now(), atualizado_por = excluded.atualizado_por",
					campo.chave(), valor, atorId);
		}
		auditoria.registrar(atorId, "CONFIGURACAO_ALTERADA", "configuracao", null, "chaves=" + novos.keySet());
		return todas();
	}
}
