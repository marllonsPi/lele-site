package br.com.crispimcampos.gestao.calendario;

/** Onde o prazo corre: tribunal do processo e, quando conhecidos, UF e municipio da comarca. */
public record ContextoCalendario(String tribunal, String uf, String municipio) {

	public static ContextoCalendario deTribunal(String tribunal) {
		return new ContextoCalendario(tribunal, null, null);
	}
}
