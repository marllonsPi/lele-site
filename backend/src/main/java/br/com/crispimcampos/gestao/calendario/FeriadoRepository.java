package br.com.crispimcampos.gestao.calendario;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FeriadoRepository extends JpaRepository<Feriado, Long> {

	@Query("select f from Feriado f where f.ativo = true and f.dataFinal >= :de and f.dataInicial <= :ate")
	List<Feriado> findAtivosNoPeriodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

	@Query("select count(f) > 0 from Feriado f where f.ativo = true and f.escopo = 'NACIONAL' "
			+ "and f.dataFinal >= :de and f.dataInicial <= :ate")
	boolean existeNacionalNoPeriodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

	List<Feriado> findAllByOrderByDataInicialAsc();
}
