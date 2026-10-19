package br.com.crispimcampos.gestao.acesso;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegraVisibilidadeRepository extends JpaRepository<RegraVisibilidade, Long> {

	List<RegraVisibilidade> findByObservadorId(Long observadorId);
}
