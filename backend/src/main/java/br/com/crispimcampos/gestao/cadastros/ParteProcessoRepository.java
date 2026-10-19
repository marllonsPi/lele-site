package br.com.crispimcampos.gestao.cadastros;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ParteProcessoRepository extends JpaRepository<ParteProcesso, Long> {

	List<ParteProcesso> findByProcessoIdOrderByIdAsc(Long processoId);
}
