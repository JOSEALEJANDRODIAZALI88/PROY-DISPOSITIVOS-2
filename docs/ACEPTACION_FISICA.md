# Aceptación en teléfonos — pendiente de ejecución

Esta guía no es evidencia de aprobación. Los emuladores y los OTP administrativos
no sustituyen las pruebas siguientes. No incluir contraseñas, OTP, claves, valores
de salud, personas identificables ni coordenadas exactas en capturas compartidas.

## Preparación segura

1. Usar un Android 8/API 26 o posterior, preferentemente un teléfono de pruebas.
2. Instalar `app/build/outputs/apk/debug/app-debug.apk` y anotar su SHA-256.
   Si ya hay una instalación con datos importantes, no desinstalarla ni borrar
   su almacenamiento. Ante conflicto de firma, detenerse y consultar.
3. **No ejecutar `connectedDebugAndroidTest` en el teléfono personal**:
   Gradle puede desinstalar la app. El controlador cloud también rechaza seriales físicos.
4. Anotar modelo/API, versión APK, permisos concedidos, presencia de sensor de pasos
   y barómetro. Es válido que estos sensores no existan; registrar ese caso.
5. Los datos guardados permanecen en el teléfono salvo publicaciones urbanas consentidas.
   No hacer pruebas destructivas sobre perfiles o fotografías personales.

## 1. Correo y sesión

- En Perfil → Cuenta, crear una cuenta propia nueva, con contraseña de al menos 10 caracteres.
- Revisar Recibidos y Spam. Introducir el código de ocho dígitos **solo en la app**.
- Comprobar que un código equivocado no inicia sesión; no agotar los límites de intentos.
- Confirmar el código correcto, aceptar por separado el nombre/perfil público y entrar en Ciudad.
- Cerrar/reabrir la aplicación: comprobar sesión persistente y acceso al mapa.
- Cerrar sesión y comprobar que las opciones compartidas requieren autenticación.
- Si se prueba reenvío, respetar el tiempo de espera; no repetir solicitudes automáticamente.

El asistente `scripts/check-email-delivery.py` permite comprobar previamente el mismo servicio
con un alias propio temporal. Esa prueba de escritorio no acredita la interacción Android.

## 2. GPS continuo: al menos 20 minutos reales

Hacerlo al aire libre y en un recorrido seguro. No manipular el teléfono al conducir:
las pruebas de vehículos se realizan **como pasajero**.

1. En Actividad, indicar honestamente la actividad realizada y revisar la ventana de detección.
2. Conceder ubicación precisa. Aceptar notificaciones si están disponibles y, opcionalmente, pasos.
3. Iniciar y esperar una posición utilizable. Registrar hora de inicio.
4. Apagar la pantalla y mantener al menos **20 minutos continuos**, sin pausas intencionales.
5. Comprobar que sigue existiendo la notificación de grabación.
6. Volver a la app, finalizar y revisar historial, duración, segmentos y trazado.
7. Contrastar distancia con una referencia independiente. Registrar error observado,
   no declarar exactitud si no existe una referencia.
8. Revisar continuidad temporal de puntos y **ausencia de huecos superiores a 30 segundos**
   durante el tramo continuo. Si no se dispone de esa medición, dejar este criterio pendiente.
9. Si falló, registrar pérdida de señal, entorno, ahorro de batería y estado de permisos;
   no ocultar fallos ni desactivar globalmente la seguridad del teléfono.

En pruebas separadas:

- Caminar y correr: no convertir una carrera en vehículo solo por superar 7 km/h.
- Minibús y Teleférico: conservar sus segmentos; excluir vehículo/desconocido de metros deportivos.
- Pausar, esperar, continuar y finalizar: la pausa no suma tiempo deportivo ni une el mapa
  atravesando el intervalo sin posiciones.
- Reabrir tras finalizar y reiniciar el teléfono: la sesión guardada debe persistir.
- Denegar ubicación, conceder solo aproximada y denegar pasos: mostrar límites claros;
  perfil y coordenadas manuales siguen disponibles.

**Límite conocido:** el detector usa velocidad, pasos disponibles y actividad indicada por el
usuario. No equivale a un SDK de reconocimiento automático validado en campo.
No hay reanudación automática del GPS después de la muerte del proceso.

### Medir los huecos sin compartir coordenadas

Después de la prueba, obtener privadamente una **copia SQLite consistente**, incluyendo
los datos confirmados del WAL. Consultar el procedimiento en [SQLITE.md](SQLITE.md);
no copiar solo el `.db` abierto e ignorar su WAL.

```powershell
python scripts/check-field-recording.py "C:\ruta-privada\copia-consistente.db" --session UUID-DE-LA-SESION
```

Solo lee estado, duración y tiempos de puntos. El informe de
`app/build/verification/field/` omite coordenadas, salud e identificadores.
Mide duración y huecos superiores a 30 segundos; las pausas cuentan como huecos.
Aunque un conjunto de tiempos cumpla el criterio, **no prueba teléfono físico, pantalla
apagada ni exactitud de transporte/distancia**. Esas observaciones siguen siendo manuales.
Las siete pruebas offline del analizador usan tiempos sintéticos, no actividad de campo.

## 3. Ciudad, IA y dos teléfonos

Con dos cuentas propias y dos teléfonos:

- Registrar plogging e incidencia con fotos propias, sin personas identificables.
- Probar cinco fotos consentidas con Gemini una vez conectado en modo gratuito.
  Revisar sugerencias y corregir una clasificación manualmente. Una respuesta de IA
  no demuestra por sí sola que se haya recogido basura.
- Comprobar fotografía, categoría, ubicación y consentimiento antes de publicar.
- La otra cuenta debe ver la publicación y foto compartidas, pero no poder resolverlas
  ni borrarlas como si fuera su autora.
- Resolver/eliminar desde la cuenta autora y comprobar la actualización en la otra.
- Verificar puntos de servidor: poco/medio/mucho = 5/10/20; incidencias = 0.
- Separar borrar la copia local de borrar la publicación remota. No borrar una cuenta real
  como prueba: usar únicamente una cuenta desechable autorizada.

## 4. Offline, accesibilidad y operación

- Sin red: consultar el perfil y los registros locales; no simular una publicación exitosa.
- Volver a la red y reintentar: no duplicar el registro ni sus puntos.
- Aumentar tamaño de texto y recorrer formularios con TalkBack: comprobar foco, etiquetas,
  botones accesibles y ausencia de superposición.
- Simular poco espacio solo en un dispositivo/entorno descartable, nunca llenando el teléfono
  personal ni borrando archivos ajenos. Registrar el error y conservación de datos.
- Seguir el procedimiento de respaldos de [SERVIDOR.md](SERVIDOR.md). No considerar aceptada
  la restauración/retención del proveedor hasta haberla ejecutado y comprobado.

## Registro mínimo de resultados

| Fecha / dispositivo / API | APK SHA-256 | Criterio | Pasó / falló / pendiente | Evidencia sin datos privados |
|---|---|---|---|---|
| Por completar | Por completar | Por completar | Pendiente | Por completar |

Anotar resultados reales en [VERIFICACION.md](VERIFICACION.md) antes de cambiar el estado de
los tickets. No sustituir mediciones faltantes por estimaciones ni marcar toda la entrega Done.
