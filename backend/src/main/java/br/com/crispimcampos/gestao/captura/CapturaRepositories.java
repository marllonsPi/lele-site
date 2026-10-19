package br.com.crispimcampos.gestao.captura;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PublicacaoBrutaRepository extends JpaRepository<PublicacaoBruta, Long> {

	boolean existsByFonteAndIdExternoAndTitularId(Fonte fonte, String idExterno, Long titularId);
}

interface PublicacaoRepository extends JpaRepository<Publicacao, Long> {

	Optional<Publicacao> findByTitularIdAndNumeroProcessoAndDataPublicacao(Long titularId, String numero, LocalDate data);

	List<Publicacao> findByTitularIdInOrderByDataPublicacaoDescIdDesc(Collection<Long> titulares);

	List<Publicacao> findByTitularIdInAndEstadoOrderByDataPublicacaoDescIdDesc(Collection<Long> titulares, EstadoPublicacao estado);

	long countByTitularIdInAndEstado(Collection<Long> titulares, EstadoPublicacao estado);
}

interface FiltroCapturaRepository extends JpaRepository<FiltroCaptura, Long> {

	List<FiltroCaptura> findByUsuarioIdAndFonteAndAtivoTrue(Long usuarioId, Fonte fonte);

	List<FiltroCaptura> findAllByOrderByIdAsc();
}
