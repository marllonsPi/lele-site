package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.cadastros.NumeroCnj;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Do bruto a fila "A classificar" (arquitetura 5). Idempotente: o mesmo item nunca entra duas vezes; itens da mesma
 * publicacao vindos de fontes diferentes viram uma publicacao com varias fontes; o que o filtro ignora fica
 * registrado em item_ignorado.
 */
@Service
public class PipelineCaptura {

	public record Resultado(int lidos, int duplicados, int ignorados, int novasPublicacoes, int mescladas) {

		static Resultado soma(Resultado a, Resultado b) {
			return new Resultado(a.lidos + b.lidos, a.duplicados + b.duplicados, a.ignorados + b.ignorados,
					a.novasPublicacoes + b.novasPublicacoes, a.mescladas + b.mescladas);
		}

		public static Resultado vazio() {
			return new Resultado(0, 0, 0, 0, 0);
		}
	}

	private final PublicacaoBrutaRepository brutas;
	private final PublicacaoRepository publicacoes;
	private final FiltroCapturaRepository filtros;
	private final CadastroService cadastros;
	private final JdbcTemplate jdbc;

	PipelineCaptura(PublicacaoBrutaRepository brutas, PublicacaoRepository publicacoes, FiltroCapturaRepository filtros,
			CadastroService cadastros, JdbcTemplate jdbc) {
		this.brutas = brutas;
		this.publicacoes = publicacoes;
		this.filtros = filtros;
		this.cadastros = cadastros;
		this.jdbc = jdbc;
	}

	@Transactional
	public Resultado processar(Long titularId, List<DadosBrutos> itens) {
		Resultado total = Resultado.vazio();
		for (DadosBrutos d : itens) {
			total = Resultado.soma(total, um(titularId, d));
		}
		return total;
	}

	private Resultado um(Long titularId, DadosBrutos d) {
		if (brutas.existsByFonteAndIdExternoAndTitularId(d.fonte(), d.idExterno(), titularId)) {
			return new Resultado(1, 1, 0, 0, 0);
		}
		PublicacaoBruta bruta = brutas.save(new PublicacaoBruta(titularId, d));
		for (FiltroCaptura f : filtros.findByUsuarioIdAndFonteAndAtivoTrue(titularId, d.fonte())) {
			if (Filtros.ignora(d.tribunal(), f.getTribunalPrefixo(), f.getExcecoes())) {
				jdbc.update("insert into item_ignorado (bruta_id, regra, motivo) values (?, ?, ?)", bruta.getId(),
						"FILTRO_" + f.getId(), "Tribunal " + d.tribunal() + " ignorado pelo filtro " + f.getTribunalPrefixo()
								+ (f.getExcecoes() == null ? "" : " (exceto " + f.getExcecoes() + ")"));
				return new Resultado(1, 0, 1, 0, 0);
			}
		}
		String numero = chave(d);
		var existente = publicacoes.findByTitularIdAndNumeroProcessoAndDataPublicacao(titularId, numero, d.dataPublicacao());
		boolean nova = existente.isEmpty();
		Publicacao p = existente.orElseGet(() -> publicacoes.save(new Publicacao(numero, titularId, d.dataPublicacao(),
				d.tribunal(), resumo(d.conteudo()), cadastros.processoPorNumero(numero).map(r -> r.id()).orElse(null))));
		jdbc.update("insert into publicacao_fonte (publicacao_id, fonte, bruta_id) values (?, ?, ?) on conflict do nothing",
				p.getId(), d.fonte().name(), bruta.getId());
		return new Resultado(1, 0, 0, nova ? 1 : 0, nova ? 0 : 1);
	}

	/** Numero CNJ normalizado; sem numero valido usa o hash do conteudo, para nao misturar publicacoes distintas. */
	static String chave(DadosBrutos d) {
		String n = NumeroCnj.normalizar(d.numeroProcesso());
		return n.length() == 20 ? n : "SN-" + d.hash().substring(0, Math.min(16, d.hash().length()));
	}

	static String resumo(String conteudo) {
		String limpo = conteudo.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
		return limpo.length() <= 400 ? limpo : limpo.substring(0, 397) + "...";
	}
}
