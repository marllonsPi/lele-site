package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Filtros por usuario e fonte, editaveis pelas socias (invariante 10). */
@Service
public class FiltroService {

	public record FiltroDto(Long id, Long usuarioId, Fonte fonte, String tribunalPrefixo, String excecoes, boolean ativo) {
	}

	private final FiltroCapturaRepository filtros;
	private final AuditoriaService auditoria;

	FiltroService(FiltroCapturaRepository filtros, AuditoriaService auditoria) {
		this.filtros = filtros;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<FiltroDto> listar() {
		return filtros.findAllByOrderByIdAsc().stream().map(FiltroService::dto).toList();
	}

	@Transactional
	public FiltroDto criar(Long atorId, Long usuarioId, Fonte fonte, String prefixo, String excecoes) {
		if (Filtros.normalizar(prefixo).isEmpty()) {
			throw new IllegalArgumentException("Informe o prefixo do tribunal");
		}
		FiltroCaptura f = filtros.save(new FiltroCaptura(usuarioId, fonte, prefixo.trim(),
				excecoes == null || excecoes.isBlank() ? null : excecoes.trim()));
		auditoria.registrar(atorId, "FILTRO_CRIADO", "filtro_captura", String.valueOf(f.getId()), null);
		return dto(f);
	}

	@Transactional
	public void desativar(Long atorId, Long id) {
		filtros.findById(id).orElseThrow(() -> new CapturaNaoEncontradaException("Filtro nao encontrado")).desativar();
		auditoria.registrar(atorId, "FILTRO_DESATIVADO", "filtro_captura", String.valueOf(id), null);
	}

	private static FiltroDto dto(FiltroCaptura f) {
		return new FiltroDto(f.getId(), f.getUsuarioId(), f.getFonte(), f.getTribunalPrefixo(), f.getExcecoes(), f.isAtivo());
	}
}
