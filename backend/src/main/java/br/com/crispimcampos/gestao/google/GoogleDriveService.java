package br.com.crispimcampos.gestao.google;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Drive da conta Google conectada de uma pessoa (opt-in): le as pastas de clientes e grava anexos em pastas do escritorio. Nao apaga
 * nada. Usado pela importacao, pela gravacao de anexos e pelas sugestoes de arquivos por cliente.
 */
@Service
public class GoogleDriveService {

	/** Arquivo (nao pasta) do Drive; {@code caminho} = nomes das pastas desde a raiz pedida, sem a raiz. */
	public record ArquivoDrive(String id, String nome, String mime, Instant modificadoEm, long tamanho, List<String> caminho, List<String> pastaIds) {
	}

	/** Limite de seguranca: profundidade e quantidade de arquivos por varredura. */
	static final int PROFUNDIDADE_MAXIMA = 6;
	static final int ARQUIVOS_MAXIMOS = 5000;

	private final GoogleApi api;
	private final GoogleContasService contas;

	GoogleDriveService(GoogleApi api, GoogleContasService contas) {
		this.api = api;
		this.contas = contas;
	}

	public boolean disponivel(Long usuarioId) {
		return contas.disponivel() && contas.status(usuarioId).drive();
	}

	/** Todos os arquivos sob a pasta, recursivamente (largura primeiro). */
	public List<ArquivoDrive> listarArvore(Long usuarioId, String pastaRaizId) {
		String acesso = contas.tokenDeAcessoDrive(usuarioId);
		List<ArquivoDrive> saida = new ArrayList<>();
		record Pendente(String id, List<String> caminho, List<String> ids) {
		}
		Deque<Pendente> fila = new ArrayDeque<>();
		fila.add(new Pendente(pastaRaizId, List.of(), List.of()));
		while (!fila.isEmpty() && saida.size() < ARQUIVOS_MAXIMOS) {
			Pendente p = fila.poll();
			for (GoogleApi.ItemDrive i : api.filhos(acesso, p.id())) {
				if (i.pasta()) {
					if (p.caminho().size() < PROFUNDIDADE_MAXIMA) {
						List<String> c = new ArrayList<>(p.caminho());
						c.add(i.nome());
						List<String> ids = new ArrayList<>(p.ids());
						ids.add(i.id());
						fila.add(new Pendente(i.id(), c, ids));
					}
				} else if (saida.size() < ARQUIVOS_MAXIMOS) {
					saida.add(new ArquivoDrive(i.id(), i.nome(), i.mime(), i.modificadoEm(), i.tamanho(), p.caminho(), p.ids()));
				}
			}
		}
		return saida;
	}

	/** Conteudo do arquivo; Docs viram PDF e Planilhas viram .xlsx. */
	public byte[] baixar(Long usuarioId, ArquivoDrive arquivo) {
		String acesso = contas.tokenDeAcessoDrive(usuarioId);
		return baixarComToken(acesso, arquivo.id(), arquivo.mime());
	}

	/** Conteudo de um arquivo apenas pelo id (ex.: a planilha financeira configurada). */
	public byte[] baixarPlanilha(Long usuarioId, String arquivoId, boolean nativaDoGoogle) {
		String acesso = contas.tokenDeAcessoDrive(usuarioId);
		return nativaDoGoogle ? api.exportar(acesso, arquivoId, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
				: api.baixar(acesso, arquivoId);
	}

	private byte[] baixarComToken(String acesso, String id, String mime) {
		if (mime != null && mime.startsWith("application/vnd.google-apps.")) {
			return api.exportar(acesso, id, mime.endsWith("spreadsheet")
					? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/pdf");
		}
		return api.baixar(acesso, id);
	}

	/** Id da pasta {@code nome} dentro de {@code paiId}, reaproveitando pasta existente (sem acento/caixa) ou criando. */
	public String garantirPasta(Long usuarioId, String paiId, String nome) {
		String acesso = contas.tokenDeAcessoDrive(usuarioId);
		String alvo = chave(nome);
		for (GoogleApi.ItemDrive p : api.pastasFilhas(acesso, paiId)) {
			if (chave(p.nome()).equals(alvo)) {
				return p.id();
			}
		}
		return api.criarPasta(acesso, paiId, nome);
	}

	/** Pasta do Drive (id e nome). */
	public record PastaDrive(String id, String nome) {
	}

	/** Subpastas diretas de uma pasta. */
	public List<PastaDrive> subpastas(Long usuarioId, String paiId) {
		return api.pastasFilhas(contas.tokenDeAcessoDrive(usuarioId), paiId).stream().map(p -> new PastaDrive(p.id(), p.nome())).toList();
	}

	/** Subpasta existente com esse nome (sem acento/caixa), se houver; nunca cria. */
	public java.util.Optional<String> acharPasta(Long usuarioId, String paiId, String nome) {
		String acesso = contas.tokenDeAcessoDrive(usuarioId);
		String alvo = chave(nome);
		return api.pastasFilhas(acesso, paiId).stream().filter(p -> chave(p.nome()).equals(alvo)).map(GoogleApi.ItemDrive::id).findFirst();
	}

	/** Grava o arquivo na pasta e devolve o id dele no Drive. */
	public String enviar(Long usuarioId, String pastaId, String nome, String mime, byte[] conteudo) {
		return api.enviarArquivo(contas.tokenDeAcessoDrive(usuarioId), pastaId, nome, mime, conteudo);
	}

	/** Conteudo de um arquivo pelo id, exatamente como esta no Drive. */
	public byte[] baixarPorId(Long usuarioId, String arquivoId) {
		return api.baixar(contas.tokenDeAcessoDrive(usuarioId), arquivoId);
	}

	private static String chave(String s) {
		return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toUpperCase().replaceAll("\\s+", " ").trim();
	}
}
