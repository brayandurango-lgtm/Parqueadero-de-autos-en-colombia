package com.autoscolombia.service;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.UsuarioService;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
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
    void crear_CifraClaveYActivaUsuarioPorDefecto() {
        when(usuarioRepository.findByCorreo(dto.getCorreo())).thenReturn(Optional.empty());
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(passwordEncoder.encode("secret")).thenReturn("bcrypt-hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        Usuario creado = usuarioService.crear(dto, "secret");

        assertEquals("bcrypt-hash", creado.getClaveHash());
        assertTrue(creado.getActivo());
        verify(passwordEncoder).encode("secret");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void crear_RespetaEstadoInactivoSolicitado() {
        dto.setActivo(false);
        when(usuarioRepository.findByCorreo(dto.getCorreo())).thenReturn(Optional.empty());
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        assertFalse(usuarioService.crear(dto, "secret").getActivo());
    }

    @Test
    void crear_RechazaCorreoDuplicado() {
        when(usuarioRepository.findByCorreo(dto.getCorreo())).thenReturn(Optional.of(usuario));

        assertThrows(BusinessRuleException.class, () -> usuarioService.crear(dto, "secret"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crear_RechazaDocumentoDuplicado() {
        when(usuarioRepository.findByCorreo(dto.getCorreo())).thenReturn(Optional.empty());
        when(usuarioRepository.findAll()).thenReturn(List.of(usuario));

        assertThrows(BusinessRuleException.class, () -> usuarioService.crear(dto, "secret"));

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
}