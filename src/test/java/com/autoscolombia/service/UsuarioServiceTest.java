package com.autoscolombia.service;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.UsuarioService;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.UsuarioCrearDTO;
import com.autoscolombia.presentation.dto.UsuarioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.autoscolombia.domain.service.AuditoriaService auditoriaService;

    @InjectMocks
    private UsuarioService usuarioService;

    private UsuarioDTO dto;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        dto = UsuarioDTO.builder().nombre("Ana").apellido("Pérez").documento("12345678")
                .correo("ana@example.com").telefono("3001112233").tipo(TipoUsuario.OPERADOR).build();
        usuario = Usuario.builder().id(1L).nombre("Ana").apellido("Pérez").documento("12345678")
                .correo("ana@example.com").telefono("3001112233").claveHash("encoded-password")
                .tipo(TipoUsuario.OPERADOR).activo(true).build();
    }

    @Test
    void crear_CifraLaClaveDeLaSolicitudYActivaUsuarioPorDefecto() {
        UsuarioCrearDTO solicitud = dtoCrear("Clave inicial de prueba 15");
        when(usuarioRepository.findByCorreo(solicitud.getCorreo())).thenReturn(Optional.empty());
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(passwordEncoder.encode("Clave inicial de prueba 15")).thenReturn("bcrypt-hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        Usuario creado = usuarioService.crear(solicitud);

        assertEquals("bcrypt-hash", creado.getClaveHash());
        assertTrue(creado.getActivo());
        verify(passwordEncoder).encode("Clave inicial de prueba 15");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void crear_RespetaEstadoInactivoSolicitado() {
        UsuarioCrearDTO solicitud = dtoCrear("Clave inicial de prueba 15");
        solicitud.setActivo(false);
        when(usuarioRepository.findByCorreo(solicitud.getCorreo())).thenReturn(Optional.empty());
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(passwordEncoder.encode("Clave inicial de prueba 15")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        assertFalse(usuarioService.crear(solicitud).getActivo());
    }

    @Test
    void crear_RechazaCorreoDuplicado() {
        UsuarioCrearDTO solicitud = dtoCrear("Clave inicial de prueba 15");
        when(usuarioRepository.findByCorreo(solicitud.getCorreo())).thenReturn(Optional.of(usuario));

        assertThrows(BusinessRuleException.class, () -> usuarioService.crear(solicitud));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crear_RechazaDocumentoDuplicado() {
        UsuarioCrearDTO solicitud = dtoCrear("Clave inicial de prueba 15");
        when(usuarioRepository.findByCorreo(solicitud.getCorreo())).thenReturn(Optional.empty());
        when(usuarioRepository.findAll()).thenReturn(List.of(usuario));

        assertThrows(BusinessRuleException.class, () -> usuarioService.crear(solicitud));

        verify(passwordEncoder, never()).encode(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizar_CambiaDatosSinAlterarEstadoCuandoNoSeProvee() {
        dto.setNombre("Ana María");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCorreo(dto.getCorreo())).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        Usuario actualizado = usuarioService.actualizar(1L, dto);

        assertEquals("Ana María", actualizado.getNombre());
        assertTrue(actualizado.getActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void actualizar_RechazaCorreoUsadoPorOtroUsuario() {
        Usuario otro = Usuario.builder().id(2L).correo(dto.getCorreo()).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCorreo(dto.getCorreo())).thenReturn(Optional.of(otro));

        assertThrows(BusinessRuleException.class, () -> usuarioService.actualizar(1L, dto));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizar_RechazaDocumentoDeOtroUsuarioSinModificarElOriginal() {
        Usuario otro = Usuario.builder().id(2L).documento("87654321").nombre("Luis").build();
        dto.setDocumento("87654321");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByDocumento("87654321")).thenReturn(Optional.of(otro));

        assertThrows(BusinessRuleException.class, () -> usuarioService.actualizar(1L, dto));

        verify(usuarioRepository, never()).save(any());
        assertEquals("12345678", usuario.getDocumento());
        assertEquals("Ana", usuario.getNombre());
    }

    @Test
    void actualizar_PermiteConservarElDocumentoPropio() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByDocumento("12345678")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("12345678", usuarioService.actualizar(1L, dto).getDocumento());
    }

    @Test
    void actualizar_PermiteDocumentoNuevoNoAsignado() {
        dto.setDocumento("87654321");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByDocumento("87654321")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("87654321", usuarioService.actualizar(1L, dto).getDocumento());
    }

    @Test
    void actualizar_UsaSoloBusquedasDirectasYNoCargaLaTabla() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        usuarioService.actualizar(1L, dto);

        verify(usuarioRepository, never()).findAll();
    }

    @Test
    void actualizar_RechazaIdInexistente() {
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> usuarioService.actualizar(9L, dto));
    }

    @Test
    void eliminar_DesactivaEnLugarDeBorrar() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        usuarioService.eliminar(1L);

        assertFalse(usuario.getActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void eliminar_RechazaIdInexistente() {
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> usuarioService.eliminar(9L));
    }

    @Test
    void obtenerTodos_NoExponeHashYMapeaPerfil() {
        when(usuarioRepository.findAll()).thenReturn(List.of(usuario));

        List<UsuarioDTO> result = usuarioService.obtenerTodos();

        assertEquals(1, result.size());
        assertEquals("ana@example.com", result.get(0).getCorreo());
        assertEquals("Ana", result.get(0).getNombre());
        assertEquals(TipoUsuario.OPERADOR, result.get(0).getTipo());
        assertEquals("12345678", result.get(0).getDocumento());
    }

    @Test
    void obtenerPorId_RechazaIdInexistente() {
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> usuarioService.obtenerPorId(9L));
    }

    @Test
    void obtenerPorCorreo_DevuelveRespuestaDelRepositorio() {
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.of(usuario));

        assertEquals(usuario, usuarioService.obtenerPorCorreo("ana@example.com").orElseThrow());
    }

    // -------------------------------------------------- estado y último administrador activo

    private UsuarioCrearDTO dtoCrear(String clave) {
        return UsuarioCrearDTO.builder().nombre("Ana").apellido("Pérez").documento("12345678")
                .correo("ana@example.com").telefono("3001112233").tipo(TipoUsuario.OPERADOR).clave(clave)
                .build();
    }

    private Usuario administradorActivo(long id) {
        return Usuario.builder().id(id).nombre("Ana").apellido("Pérez").documento("12345678")
                .correo("ana@example.com").telefono("3001112233").claveHash("hash")
                .tipo(TipoUsuario.ADMINISTRADOR).activo(true).build();
    }

    @Test
    void cambiarEstado_RechazaDesactivarAlUltimoAdministradorActivoYSinGuardarNada() {
        Usuario admin = administradorActivo(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByTipoAndActivo(TipoUsuario.ADMINISTRADOR, true)).thenReturn(List.of(admin));

        assertThrows(BusinessRuleException.class, () -> usuarioService.cambiarEstado(1L, false));

        verify(usuarioRepository, never()).save(any());
        assertTrue(admin.getActivo());
        assertEquals(TipoUsuario.ADMINISTRADOR, admin.getTipo());
    }

    @Test
    void cambiarEstado_PermiteDesactivarAlAdministradorCuandoHayOtroActivo() {
        Usuario admin = administradorActivo(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByTipoAndActivo(TipoUsuario.ADMINISTRADOR, true))
                .thenReturn(List.of(admin, administradorActivo(2L)));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        assertFalse(usuarioService.cambiarEstado(1L, false).getActivo());
        verify(auditoriaService).registrar(eq("USUARIO_DESACTIVAR"), eq("Usuario"), eq(1L), isNull(), eq("EXITO"), contains("Desactivación"), any());
    }

    @Test
    void cambiarEstado_PermiteActivarYRegistraAuditoria() {
        Usuario inactivo = Usuario.builder().id(3L).correo("op@autoscolombia.com").activo(false).tipo(TipoUsuario.OPERADOR).build();
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(inactivo));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        assertTrue(usuarioService.cambiarEstado(3L, true).getActivo());
        verify(auditoriaService).registrar(eq("USUARIO_ACTIVAR"), eq("Usuario"), eq(3L), isNull(), eq("EXITO"), contains("Activación"), any());
    }

    @Test
    void cambiarEstado_RepetirElEstadoVigenteNoEscribeNiConsultaAdministradores() {
        Usuario admin = administradorActivo(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertSame(admin, usuarioService.cambiarEstado(1L, true));

        verify(usuarioRepository, never()).save(any());
        verify(usuarioRepository, never()).findByTipoAndActivo(any(), any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void cambiarEstado_DejaDesactivarUnOperadorSinConsultarLaReglaDelAdministrador() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        assertFalse(usuarioService.cambiarEstado(1L, false).getActivo());
        verify(auditoriaService).registrar(eq("USUARIO_DESACTIVAR"), eq("Usuario"), eq(1L), isNull(), eq("EXITO"), contains("Desactivación"), any());
        verify(usuarioRepository, never()).findByTipoAndActivo(any(), any());
    }

    @Test
    void cambiarEstado_RechazaIdentificadorInexistente() {
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> usuarioService.cambiarEstado(9L, false));
    }

    @Test
    void actualizar_RechazaDegradarAlUltimoAdministradorSinModificarElOriginal() {
        Usuario admin = administradorActivo(1L);
        dto.setNombre("Nombre cambiado");
        dto.setTipo(TipoUsuario.OPERADOR);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByTipoAndActivo(TipoUsuario.ADMINISTRADOR, true)).thenReturn(List.of(admin));

        assertThrows(BusinessRuleException.class, () -> usuarioService.actualizar(1L, dto));

        verify(usuarioRepository, never()).save(any());
        assertEquals("Ana", admin.getNombre());
        assertEquals(TipoUsuario.ADMINISTRADOR, admin.getTipo());
    }

    @Test
    void actualizar_RechazaDesactivarAlUltimoAdministradorPorLaRutaPut() {
        Usuario admin = administradorActivo(1L);
        dto.setTipo(TipoUsuario.ADMINISTRADOR);
        dto.setActivo(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByTipoAndActivo(TipoUsuario.ADMINISTRADOR, true)).thenReturn(List.of(admin));

        assertThrows(BusinessRuleException.class, () -> usuarioService.actualizar(1L, dto));

        verify(usuarioRepository, never()).save(any());
        assertTrue(admin.getActivo());
    }

    @Test
    void actualizar_PermiteDegradarCuandoExisteOtroAdministradorActivo() {
        Usuario admin = administradorActivo(1L);
        dto.setTipo(TipoUsuario.OPERADOR);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByTipoAndActivo(TipoUsuario.ADMINISTRADOR, true))
                .thenReturn(List.of(admin, administradorActivo(2L)));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals(TipoUsuario.OPERADOR, usuarioService.actualizar(1L, dto).getTipo());
    }

    @Test
    void actualizar_EditarOtrosDatosDelUltimoAdministradorNoDisparaLaRegla() {
        Usuario admin = administradorActivo(1L);
        dto.setNombre("Nombre cambiado");
        dto.setTipo(TipoUsuario.ADMINISTRADOR);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("Nombre cambiado", usuarioService.actualizar(1L, dto).getNombre());

        verify(usuarioRepository, never()).findByTipoAndActivo(any(), any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void eliminar_TambienRespetaLaReglaDelUltimoAdministradorActivo() {
        Usuario admin = administradorActivo(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByTipoAndActivo(TipoUsuario.ADMINISTRADOR, true)).thenReturn(List.of(admin));

        assertThrows(BusinessRuleException.class, () -> usuarioService.eliminar(1L));

        verify(usuarioRepository, never()).save(any());
        assertTrue(admin.getActivo());
    }
}
