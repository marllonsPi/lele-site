package br.com.crispimcampos.gestao.contas;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String email);

	long countByPerfilAndAtivoTrue(Perfil perfil);

	List<Usuario> findBySupervisorIdAndAtivoTrue(Long supervisorId);
}
