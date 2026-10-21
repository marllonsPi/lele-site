package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.google.GoogleDriveService;
import br.com.crispimcampos.gestao.google.GoogleDriveService.ArquivoDrive;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Cria a fonte do Drive de uma pessoa (a conta Google conectada dela, com acesso ao Drive) para a pasta raiz de clientes configurada. */
@Component
class DriveFontes {

	private final GoogleDriveService drive;
	private final String pastaClientes;
	private final String planilhaId;

	DriveFontes(GoogleDriveService drive, @Value("${app.importacao.pasta-clientes-id:}") String pastaClientes,
			@Value("${app.importacao.planilha-id:}") String planilhaId) {
		this.drive = drive;
		this.pastaClientes = pastaClientes;
		this.planilhaId = planilhaId;
	}

	boolean configurada() {
		return !pastaClientes.isBlank();
	}

	boolean planilhaConfigurada() {
		return !planilhaId.isBlank();
	}

	boolean disponivel(Long usuarioId) {
		return drive.disponivel(usuarioId);
	}

	DriveFonte para(Long usuarioId) {
		if (pastaClientes.isBlank()) {
			throw new IllegalStateException("Pasta de clientes do Drive nao configurada (IMPORTACAO_PASTA_CLIENTES_ID)");
		}
		return new DriveFonte() {
			@Override
			public List<Arquivo> listar() {
				return drive.listarArvore(usuarioId, pastaClientes).stream()
						.map(a -> new Arquivo(a.id(), a.nome(), a.mime(), a.modificadoEm(), a.tamanho(), a.caminho())).toList();
			}

			@Override
			public byte[] baixar(Arquivo a) {
				return drive.baixar(usuarioId, new ArquivoDrive(a.id(), a.nome(), a.mime(), a.modificadoEm(), a.tamanho(), a.caminho(), List.of()));
			}
		};
	}

	/** A planilha financeira no Drive (arquivo .xlsx ou Google Planilhas), como bytes .xlsx. */
	byte[] baixarPlanilha(Long usuarioId) {
		if (planilhaId.isBlank()) {
			throw new IllegalStateException("Planilha financeira do Drive nao configurada (IMPORTACAO_PLANILHA_ID)");
		}
		return drive.baixarPlanilha(usuarioId, planilhaId, true);
	}
}
