package br.com.crispimcampos.gestao.contas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface OabRepository extends JpaRepository<Oab, Long> {

	List<Oab> findByUsuarioIdOrderByPrincipalDescIdAsc(Long usuarioId);

	boolean existsByNumeroAndUf(String numero, String uf);
}
