package com.autoscolombia.integration;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.model.Registro;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.model.Vehiculo;
import com.autoscolombia.infrastructure.repository.AuditoriaLogRepository;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import com.autoscolombia.infrastructure.repository.ComprobanteRepository;
import com.autoscolombia.infrastructure.repository.NovedadRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.infrastructure.repository.VehiculoRepository;
import com.autoscolombia.presentation.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cuentas del personal y permisos (bloque B1).
 *
 * <p>Las pruebas de autenticaciÃ³n usan JWT reales obtenidos en {@code /api/v1/auth/login}: ninguna
 * sustituye la cadena de seguridad por un usuario simulado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CuentasPersonalIntegrationTest {

    /** Clave vÃ¡lida: 25 caracteres, 26 bytes UTF-8, con espacios y una vocal acentuada. */
    private static final String CLAVE_VALIDA = "Clave de prueba vÃ¡lida 15";
    private static final String CLAVE_ADMIN = "Clave administrativa de prueba I2";

    private static final String RUTA_USUARIOS = "/api/v1/usuarios";

    private static final AtomicLong SECUENCIA = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RegistroRepository registroRepository;

    @Autowired
    private ComprobanteRepository comprobanteRepository;

    @Autowired
    private NovedadRepository novedadRepository;

    @Autowired
    private CeldaRepository celdaRepository;

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @Autowired
    private AuditoriaLogRepository auditoriaLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Usuario ultimoAdministrador;
    private String tokenAdmin;

    @BeforeEach
    void setUp() throws Exception {
        comprobanteRepository.deleteAll();
        novedadRepository.deleteAll();
        registroRepository.deleteAll();
        auditoriaLogRepository.deleteAll();
        celdaRepository.deleteAll();
        vehiculoRepository.deleteAll();
        usuarioRepository.deleteAll();

        ultimoAdministrador = seed(TipoUsuario.ADMINISTRADOR, true, CLAVE_ADMIN);
        tokenAdmin = login(ultimoAdministrador.getCorreo(), CLAVE_ADMIN);
    }

    // ------------------------------------------------------------------ utilidades

    private long secuencia() {
        return SECUENCIA.incrementAndGet();
    }

    private String correo(long n) {
        return "cuenta" + n + ".b1@test.com";
    }

    private String documento(long n) {
        return "91" + String.format("%08d", n);
    }

    private Usuario seed(TipoUsuario tipo, boolean activo, String clave) {
        long n = secuencia();
        Usuario usuario = Usuario.builder()
                .nombre("Personal")
                .apellido("De prueba")
                .documento(documento(n))
                .correo(correo(n))
                .telefono("3001112233")
                .claveHash(passwordEncoder.encode(clave))
                .tipo(tipo)
                .activo(activo)
                .build();
        return usuarioRepository.save(usuario);
    }

    private String login(String correo, String clave) throws Exception {
        String cuerpo = objectMapper.writeValueAsString(
                LoginRequest.builder().correo(correo).clave(clave).build());
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(respuesta).get("token").asText();
    }

    /** Cuerpo de creaciÃ³n; si {@code claveJson} es nulo se omite la propiedad {@code clave}. */
    private String cuerpoCrear(long n, String claveJson, String tipo) {
        return "{\"nombre\":\"Brayan\",\"apellido\":\"De prueba\",\"documento\":\"" + documento(n)
                + "\",\"correo\":\"" + correo(n) + "\",\"telefono\":\"3001112233\",\"tipo\":\"" + tipo + "\""
                + (claveJson == null ? "" : ",\"clave\":" + claveJson) + "}";
    }

    private ResultActions crearConClave(String claveJson) throws Exception {
        return crearConClave(tokenAdmin, claveJson);
    }

    private ResultActions crearConClave(String token, String claveJson) throws Exception {
        long n = secuencia();
        return mockMvc.perform(post(RUTA_USUARIOS)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoCrear(n, claveJson, "OPERADOR").getBytes(StandardCharsets.UTF_8)));
    }

    private String cuerpoEditar(Usuario usuario, String nombre, String tipo, boolean activo) {
        return "{\"nombre\":\"" + nombre + "\",\"apellido\":\"" + usuario.getApellido() + "\""
                + ",\"documento\":\"" + usuario.getDocumento() + "\",\"correo\":\"" + usuario.getCorreo() + "\""
                + ",\"telefono\":\"" + usuario.getTelefono() + "\",\"tipo\":\"" + tipo + "\""
                + ",\"activo\":" + activo + "}";
    }

    private ResultActions cambiarEstado(long id, String cuerpoJson, String token) throws Exception {
        return mockMvc.perform(patch(RUTA_USUARIOS + "/" + id + "/activo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoJson.getBytes(StandardCharsets.UTF_8)));
    }

    private String jsonLiteral(String valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }

    private Usuario porCorreo(String correo) {
        return usuarioRepository.findByCorreo(correo).orElseThrow();
    }

    // ------------------------------------------------------------------ polÃ­tica de contraseÃ±a

    @Test
    void crear_conClaveValida_guardaUnHashVerificableYNoDevuelveClaveNiHash() throws Exception {
        long n = secuencia();
        long antes = usuarioRepository.count();

        String respuesta = mockMvc.perform(post(RUTA_USUARIOS)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear(n, jsonLiteral(CLAVE_VALIDA), "OPERADOR")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.correo").value(correo(n)))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.clave").doesNotExist())
                .andExpect(jsonPath("$.claveHash").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertFalse(respuesta.contains(CLAVE_VALIDA), "La clave no debe aparecer en la respuesta");
        assertEquals(antes + 1, usuarioRepository.count());

        Usuario creado = porCorreo(correo(n));
        assertNotEquals(creado.getClaveHash(), CLAVE_VALIDA, "No se almacena la clave en texto plano");
        assertTrue(passwordEncoder.matches(CLAVE_VALIDA, creado.getClaveHash()),
                "El hash persistido debe verificar la clave entregada");
        assertFalse(passwordEncoder.matches("clave123", creado.getClaveHash()),
                "No debe usarse la clave fija que el controlador pasaba antes del bloque B1");
    }

    @Test
    void crear_sinLaPropiedadClave_devuelve400YNoPersisteLaCuenta() throws Exception {
        long antes = usuarioRepository.count();
        crearConClave(null).andExpect(status().isBadRequest());
        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void crear_claveVacia_devuelve400YNoPersisteLaCuenta() throws Exception {
        long antes = usuarioRepository.count();
        crearConClave("\"\"").andExpect(status().isBadRequest());
        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void crear_claveDeSoloEspacios_devuelve400YNoPersisteLaCuenta() throws Exception {
        long antes = usuarioRepository.count();
        crearConClave(jsonLiteral("                 ")).andExpect(status().isBadRequest());
        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void crear_claveDeCatorceCaracteres_devuelve400YNoPersisteLaCuenta() throws Exception {
        long antes = usuarioRepository.count();
        crearConClave(jsonLiteral("a".repeat(14))).andExpect(status().isBadRequest());
        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void crear_claveDeQuinceCaracteres_esAceptada() throws Exception {
        crearConClave(jsonLiteral("a".repeat(15))).andExpect(status().isCreated());
    }

    @Test
    void crear_claveQueExcedeLos72Bytes_devuelve400SinTruncarNiPersistir() throws Exception {
        long antes = usuarioRepository.count();
        String excesiva = "a".repeat(73);
        assertEquals(73, excesiva.getBytes(StandardCharsets.UTF_8).length);
        crearConClave(jsonLiteral(excesiva)).andExpect(status().isBadRequest());
        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void crear_claveMultibyteQueExcedeLos72Bytes_devuelve400SinTruncar() throws Exception {
        long antes = usuarioRepository.count();
        String agudos = "Ã¡".repeat(37);
        agudos = "\u00E1".repeat(37);
        assertEquals(37, agudos.length(), "37 caracteres");
        assertEquals(74, agudos.getBytes(StandardCharsets.UTF_8).length, "74 bytes UTF-8");

        crearConClave(jsonLiteral(agudos)).andExpect(status().isBadRequest());
        assertEquals(antes, usuarioRepository.count(), "Una clave rechazada no deja cuenta persistida");
    }

    @Test
    void crear_claveMultibyteJustoSobreElLimiteDe72Bytes_esAceptada() throws Exception {
        String agudos = "Ã¡".repeat(36);
        agudos = "\u00E1".repeat(36);
        assertEquals(72, agudos.getBytes(StandardCharsets.UTF_8).length, "72 bytes UTF-8 exactos");
        crearConClave(jsonLiteral(agudos)).andExpect(status().isCreated());
    }

    @Test
    void crear_claveConEspaciosYUnicode_seConservaExactaYPermiteIniciarSesion() throws Exception {
        String clave = "  Clave con espacios  y \u00F1  ";
        long n = secuencia();

        mockMvc.perform(post(RUTA_USUARIOS)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear(n, jsonLiteral(clave), "OPERADOR")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated());

        assertTrue(passwordEncoder.matches(clave, porCorreo(correo(n)).getClaveHash()),
                "La clave debe guardarse tal como llegÃ³, sin recortar espacios ni normalizar");
        login(correo(n), clave);
    }

    @Test
    void crear_cuerpoMalformado_devuelve400YSinFiltrarElContenido() throws Exception {
        long antes = usuarioRepository.count();
        mockMvc.perform(post(RUTA_USUARIOS)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"correo\":\"roto@test.com\",\"clave\":\"" + CLAVE_VALIDA + "\"")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(CLAVE_VALIDA))))
                .andExpect(content().string(not(containsString("roto@test.com"))));
        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void crear_sinCuerpoHttp_devuelve400() throws Exception {
        long antes = usuarioRepository.count();
        mockMvc.perform(post(RUTA_USUARIOS)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        assertEquals(antes, usuarioRepository.count());
    }

    // ------------------------------------------------------------------ ediciÃ³n de datos

    @Test
    void actualizar_sinClave_conservaElHashPrevio() throws Exception {
        Usuario operador = seed(TipoUsuario.OPERADOR, true, CLAVE_VALIDA);
        String hashPrevio = operador.getClaveHash();

        mockMvc.perform(put(RUTA_USUARIOS + "/" + operador.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(operador, "Nombre editado", "OPERADOR", true)
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre editado"))
                .andExpect(jsonPath("$.clave").doesNotExist())
                .andExpect(jsonPath("$.claveHash").doesNotExist());

        assertEquals(hashPrevio, porCorreo(operador.getCorreo()).getClaveHash(),
                "Editar los datos de una cuenta jamÃ¡s cambia su hash");
    }

    // ------------------------------------------------------------------ permisos

    @Test
    void administradorPuedeListarCrearEditarYCambiarElEstado() throws Exception {
        mockMvc.perform(get(RUTA_USUARIOS).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("claveHash"))));

        crearConClave(jsonLiteral(CLAVE_VALIDA)).andExpect(status().isCreated());

        mockMvc.perform(put(RUTA_USUARIOS + "/" + ultimoAdministrador.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(ultimoAdministrador, ultimoAdministrador.getNombre(),
                                "ADMINISTRADOR", true).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        Usuario otro = seed(TipoUsuario.OPERADOR, true, CLAVE_VALIDA);
        cambiarEstado(otro.getId(), "{\"activo\":false}", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void operadorRecibe403EnTodaLaGestionDeCuentas() throws Exception {
        Usuario operador = seed(TipoUsuario.OPERADOR, true, CLAVE_VALIDA);
        String tokenOperador = login(operador.getCorreo(), CLAVE_VALIDA);
        long cuentasAntes = usuarioRepository.count();

        mockMvc.perform(get(RUTA_USUARIOS).header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isForbidden());
        crearConClave(tokenOperador, jsonLiteral(CLAVE_VALIDA)).andExpect(status().isForbidden());
        mockMvc.perform(put(RUTA_USUARIOS + "/" + operador.getId())
                        .header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(operador, "Intento", "OPERADOR", true)
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());
        cambiarEstado(operador.getId(), "{\"activo\":false}", tokenOperador)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertTrue(porCorreo(operador.getCorreo()).getActivo(),
                "El 403 no debe haber desactivado la cuenta");
        assertEquals(cuentasAntes, usuarioRepository.count(),
                "El 403 no debe haber creado una cuenta nueva");
    }

    @Test
    void sinTokenRecibe401ConElFormatoDeErrorDeSeguridad() throws Exception {
        mockMvc.perform(get(RUTA_USUARIOS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void tokenInvalidoRecibe401() throws Exception {
        mockMvc.perform(get(RUTA_USUARIOS).header("Authorization", "Bearer no.es.un.token"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ cuenta inactiva con JWT real

    @Test
    void jwtRealDeUnaCuentaDesactivadaRecibe401SinEfectosYAlReactivarVuelveAFuncionar() throws Exception {
        Usuario operador = seed(TipoUsuario.OPERADOR, true, CLAVE_VALIDA);
        String token = login(operador.getCorreo(), CLAVE_VALIDA);
        long registrosAntes = registroRepository.count();
        long auditoriaAntes = auditoriaLogRepository.count();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(operador.getCorreo()));

        cambiarEstado(operador.getId(), "{\"activo\":false}", tokenAdmin).andExpect(status().isOk());
        auditoriaAntes = auditoriaLogRepository.count();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/registros/entrada")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"XYZ987\",\"marca\":\"Mazda\",\"modelo\":\"3\","
                                + "\"color\":\"Azul\",\"tipo\":\"CARRO\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());

        assertEquals(registrosAntes, registroRepository.count(),
                "La peticiÃ³n rechazada no debe dejar registros");
        assertEquals(auditoriaAntes, auditoriaLogRepository.count(),
                "La peticiÃ³n rechazada no debe escribir auditorÃ­a");

        Usuario inactiva = porCorreo(operador.getCorreo());
        assertFalse(inactiva.getActivo());
        assertEquals(operador.getClaveHash(), inactiva.getClaveHash());

        // LÃ­mite del mecanismo, documentado: consultar `activo` no revoca el token de forma permanente.
        cambiarEstado(operador.getId(), "{\"activo\":true}", tokenAdmin).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void loginDeCuentaInactivaSigueRechazado() throws Exception {
        Usuario operador = seed(TipoUsuario.OPERADOR, true, CLAVE_VALIDA);
        cambiarEstado(operador.getId(), "{\"activo\":false}", tokenAdmin).andExpect(status().isOk());

        String cuerpo = objectMapper.writeValueAsString(
                LoginRequest.builder().correo(operador.getCorreo()).clave(CLAVE_VALIDA).build());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ Ãºltimo administrador activo

    @Test
    void desactivarAlUltimoAdministradorActivoDevuelve409SinModificacionParcial() throws Exception {
        cambiarEstado(ultimoAdministrador.getId(), "{\"activo\":false}", tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("administrador")));

        Usuario sinCambiar = porCorreo(ultimoAdministrador.getCorreo());
        assertTrue(sinCambiar.getActivo(), "La cuenta sigue activa");
        assertEquals(TipoUsuario.ADMINISTRADOR, sinCambiar.getTipo());
    }

    @Test
    void degradarAlUltimoAdministradorActivoPorPutDevuelve409SinCambiarElTipo() throws Exception {
        mockMvc.perform(put(RUTA_USUARIOS + "/" + ultimoAdministrador.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(ultimoAdministrador, ultimoAdministrador.getNombre(),
                                "OPERADOR", true).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isConflict());

        assertEquals(TipoUsuario.ADMINISTRADOR, porCorreo(ultimoAdministrador.getCorreo()).getTipo());
        assertTrue(porCorreo(ultimoAdministrador.getCorreo()).getActivo());
    }

    @Test
    void elPutNoEsUnaRutaAlternativaParaDesactivarAlUltimoAdministrador() throws Exception {
        mockMvc.perform(put(RUTA_USUARIOS + "/" + ultimoAdministrador.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(ultimoAdministrador, "Cambio de nombre", "ADMINISTRADOR", false)
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isConflict());

        assertTrue(porCorreo(ultimoAdministrador.getCorreo()).getActivo());
    }

    @Test
    void elBorradoLogicoTampocoDesactivaAlUltimoAdministrador() throws Exception {
        mockMvc.perform(delete(RUTA_USUARIOS + "/" + ultimoAdministrador.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());

        assertTrue(porCorreo(ultimoAdministrador.getCorreo()).getActivo());
    }

    @Test
    void editarOtrosDatosDelUltimoAdministradorEstaPermitido() throws Exception {
        mockMvc.perform(put(RUTA_USUARIOS + "/" + ultimoAdministrador.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(ultimoAdministrador, "Nombre cambiado", "ADMINISTRADOR", true)
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        assertEquals("Nombre cambiado", porCorreo(ultimoAdministrador.getCorreo()).getNombre());
    }

    @Test
    void conOtroAdministradorActivoLaDesactivacionSePermite() throws Exception {
        Usuario segundoAdmin = seed(TipoUsuario.ADMINISTRADOR, true, CLAVE_ADMIN);

        cambiarEstado(ultimoAdministrador.getId(), "{\"activo\":false}", tokenAdmin)
                .andExpect(status().isOk());

        assertFalse(porCorreo(ultimoAdministrador.getCorreo()).getActivo());
        assertTrue(porCorreo(segundoAdmin.getCorreo()).getActivo(),
                "El administrador que queda sigue activo");
    }

    @Test
    void conOtroAdministradorActivoLaDegradacionSePermite() throws Exception {
        Usuario segundoAdmin = seed(TipoUsuario.ADMINISTRADOR, true, CLAVE_ADMIN);

        mockMvc.perform(put(RUTA_USUARIOS + "/" + segundoAdmin.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(segundoAdmin, "Ahora operador", "OPERADOR", true)
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        assertEquals(TipoUsuario.OPERADOR, porCorreo(segundoAdmin.getCorreo()).getTipo());
        assertTrue(porCorreo(ultimoAdministrador.getCorreo()).getActivo(),
                "La cuenta que conserva el rol sigue activa");
    }

    @Test
    void unaVezQueQuedaUnSoloAdministradorActivoEseTampocoSePuedeDegradar() throws Exception {
        Usuario segundoAdmin = seed(TipoUsuario.ADMINISTRADOR, true, CLAVE_ADMIN);
        String tokenSegundo = login(segundoAdmin.getCorreo(), CLAVE_ADMIN);

        cambiarEstado(ultimoAdministrador.getId(), "{\"activo\":false}", tokenSegundo)
                .andExpect(status().isOk());

        mockMvc.perform(put(RUTA_USUARIOS + "/" + segundoAdmin.getId())
                        .header("Authorization", "Bearer " + tokenSegundo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(segundoAdmin, "Ahora operador", "OPERADOR", true)
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isConflict());

        assertEquals(TipoUsuario.ADMINISTRADOR, porCorreo(segundoAdmin.getCorreo()).getTipo(),
                "La regla sigue protegiendo al Ãºnico administrador que queda activo");
    }

    @Test
    void desactivarUnUsuarioConHistorialConservaSusRelaciones() throws Exception {
        Usuario operador = seed(TipoUsuario.OPERADOR, true, CLAVE_VALIDA);
        Celda celda = celdaRepository.save(Celda.builder()
                .numero("Z-90").piso(9).tipoVehiculo(TipoVehiculo.CARRO).estado(EstadoCelda.OCUPADA).build());
        Vehiculo vehiculo = vehiculoRepository.save(Vehiculo.builder()
                .placa("ZZZ900").marca("Mazda").modelo("3").color("Azul").tipo(TipoVehiculo.CARRO).build());
        registroRepository.save(Registro.builder()
                .celda(celda).vehiculo(vehiculo).usuarioEntrada(operador).estado(EstadoRegistro.ACTIVO).build());

        cambiarEstado(operador.getId(), "{\"activo\":false}", tokenAdmin).andExpect(status().isOk());

        Registro historial = registroRepository.findByVehiculoPlacaAndEstado("ZZZ900", EstadoRegistro.ACTIVO)
                .orElseThrow();
        assertEquals(operador.getId(), historial.getUsuarioEntrada().getId(),
                "El historial sigue apuntando a la cuenta desactivada");
        assertEquals(operador.getDocumento(), porCorreo(operador.getCorreo()).getDocumento(),
                "La desactivaciÃ³n conserva los datos de la cuenta");
    }

    @Test
    void reiterarElMismoEstadoNoAlteraNingunOtroDato() throws Exception {
        Usuario operador = seed(TipoUsuario.OPERADOR, true, CLAVE_VALIDA);

        cambiarEstado(operador.getId(), "{\"activo\":false}", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        Usuario primeraVez = porCorreo(operador.getCorreo());

        cambiarEstado(operador.getId(), "{\"activo\":false}", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        Usuario segundaVez = porCorreo(operador.getCorreo());

        assertEquals(primeraVez.getClaveHash(), segundaVez.getClaveHash());
        assertEquals(primeraVez.getFechaRegistro(), segundaVez.getFechaRegistro());
        assertFalse(segundaVez.getActivo());
    }

    // ------------------------------------------------------------------ contrato del PATCH

    @Test
    void cambiarEstado_conActivoNuloDevuelve400() throws Exception {
        Usuario operador = seed(TipoUsuario.OPERADOR, true, CLAVE_VALIDA);
        cambiarEstado(operador.getId(), "{\"activo\":null}", tokenAdmin).andExpect(status().isBadRequest());
        assertTrue(porCorreo(operador.getCorreo()).getActivo());
    }

    @Test
    void cambiarEstado_conCuerpoSinLaPropiedadDevuelve400() throws Exception {
        Usuario operador = seed(TipoUsuario.OPERADOR, true, CLAVE_VALIDA);
        cambiarEstado(operador.getId(), "{}", tokenAdmin).andExpect(status().isBadRequest());
        assertTrue(porCorreo(operador.getCorreo()).getActivo());
    }

    @Test
    void cambiarEstado_conUsuarioInexistenteDevuelve404() throws Exception {
        cambiarEstado(999999L, "{\"activo\":false}", tokenAdmin).andExpect(status().isNotFound());
    }

    @Test
    void rutasDeEdicionConUsuarioInexistenteDevuelven404() throws Exception {
        mockMvc.perform(delete(RUTA_USUARIOS + "/999999").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(RUTA_USUARIOS + "/999999")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(ultimoAdministrador, "X", "OPERADOR", true)
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isNotFound());
    }
}
