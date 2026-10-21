package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Filtros por usuario e fonte, editaveis pelas socias (invariante 10). */
@Service
public class FiltroService {

	public record FiltroDto(Long id, Long usuarioId, Fonte fonte, String tribunalPrefixo, String excecoes, String modo, String ufs,
			String oabNumero, String oabUf, boolean ativo) {
	}

	/** modo IGNORAR: prefixo de tribunal (+ excecoes). Modo SO_UF: lista de UFs permitidas, ex. "GO,DF". OAB opcional. */
	public record DadosFiltro(Fonte fonte, String modo, String tribunalPrefixo, String excecoes, String ufs, String oabNumero,
			String oabUf) {
	}

	private static final java.util.Set<String> UFS = java.util.Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT",
			"MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SE", "SP", "TO");

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
	public FiltroDto criar(Long atorId, Long usuarioId, DadosFiltro d) {
		DadosFiltro v = validar(d);
		FiltroCaptura f = filtros.save(new FiltroCaptura(usuarioId, v.fonte(), v.tribunalPrefixo(), v.excecoes(), v.modo(), v.ufs(),
				v.oabNumero(), v.oabUf()));
		auditoria.registrar(atorId, "FILTRO_CRIADO", "filtro_captura", String.valueOf(f.getId()), null);
		return dto(f);
	}

	@Transactional
	public FiltroDto atualizar(Long atorId, Long id, DadosFiltro d, boolean ativo) {
		DadosFiltro v = validar(d);
		FiltroCaptura f = buscar(id);
		f.editar(v.fonte(), v.tribunalPrefixo(), v.excecoes(), v.modo(), v.ufs(), v.oabNumero(), v.oabUf(), ativo);
		auditoria.registrar(atorId, "FILTRO_ATUALIZADO", "filtro_captura", String.valueOf(id), null);
		return dto(f);
	}

	private static DadosFiltro validar(DadosFiltro d) {
		String modo = "SO_UF".equals(d.modo()) ? "SO_UF" : "IGNORAR";
		String oabNumero = vazio(d.oabNumero());
		String oabUf = vazio(d.oabUf()) == null ? null : d.oabUf().trim().toUpperCase(java.util.Locale.ROOT);
		if ((oabNumero == null) != (oabUf == null)) {
			throw new IllegalArgumentException("Informe o numero e a UF da OAB, ou deixe os dois em branco");
		}
		if (modo.equals("SO_UF")) {
			java.util.List<String> ufs = java.util.Arrays.stream((d.ufs() == null ? "" : d.ufs()).split("[,; ]+"))
					.map(u -> u.trim().toUpperCase(java.util.Locale.ROOT)).filter(u -> !u.isEmpty()).distinct().toList();
			if (ufs.isEmpty() || !UFS.containsAll(ufs)) {
				throw new IllegalArgumentException("Informe as UFs permitidas (ex.: GO ou GO,DF)");
			}
			return new DadosFiltro(d.fonte(), modo, null, null, String.join(",", ufs), oabNumero, oabUf);
		}
		if (Filtros.normalizar(d.tribunalPrefixo()).isEmpty()) {
			throw new IllegalArgumentException("Informe o prefixo do tribunal");
		}
		return new DadosFiltro(d.fonte(), modo, d.tribunalPrefixo().trim(), vazio(d.excecoes()), null, oabNumero, oabUf);
	}

	private static String vazio(String t) {
		return t == null || t.isBlank() ? null : t.trim();
	}

	/** Exclusao definitiva; o que o filtro ja ignorou continua no registro de ignorados. */
	@Transactional
	public void excluir(Long atorId, Long id) {
		filtros.delete(buscar(id));
		auditoria.registrar(atorId, "FILTRO_EXCLUIDO", "filtro_captura", String.valueOf(id), null);
	}

	private FiltroCaptura buscar(Long id) {
		return filtros.findById(id).orElseThrow(() -> new CapturaNaoEncontradaException("Filtro nao encontrado"));
	}

	private static FiltroDto dto(FiltroCaptura f) {
		return new FiltroDto(f.getId(), f.getUsuarioId(), f.getFonte(), f.getTribunalPrefixo(), f.getExcecoes(), f.getModo(), f.getUfs(),
				f.getOabNumero(), f.getOabUf(), f.isAtivo());
	}
}
