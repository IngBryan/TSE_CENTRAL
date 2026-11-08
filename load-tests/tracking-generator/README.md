# tracking-generator

Generador de carga de eventos de tracking (entregable 9). Simula los GPS de la flota publicando al
exchange `tracking` de RabbitMQ (routing key `tracking.event`), el mismo punto de entrada que usa el
nodo de tracking, a la tasa estimada en la Seccion 3.7.3:

```
λ = round(N × p) / T   eventos/s
```

`N` = `--vehicles` (48.370 por defecto: 22.513 profesionales + 25.857 no profesionales), `p` =
`--active-ratio`, `T` = `--interval`.

Por practicidad, los eventos los mandan 500 vehiculos simulados en vez de toda la flota, y cada uno
reporta proporcionalmente mas seguido (cada 500 / λ segundos), asi el central recibe los mismos
eventos por segundo. Ejemplo: con p = 0,4 y T = 30 s, λ = 644,9 eventos/s y cada vehiculo simulado
reporta cada 775 ms.

## Flota simulada

Esta en el `datos-prueba.sql` de Frontofficeciudadanos, asi que se carga en cada deploy: vehiculos
`LT00001` a `LT00500` de la empresa 900 "Prueba de carga", con permiso, ITV y una guia `EN_CURSO`
cada uno, y sus GPS como nodos de tracking activos (`id_externo` 100001 a 100500). Sin eso el central
descarta los eventos (GPS no registrado) o abre un caso de viaje sin guia por cada uno.

## Compilar y correr

La conexion a RabbitMQ se lee de `load-tests/tracking-generator/.env` (copiar `.env.example`; git lo
ignora). Las variables de entorno, si estan definidas, tienen prioridad. Desde una maquina con
`kubectl` al cluster, `source k8s-env.sh` las exporta a partir del Secret `tse-runtime-secrets`.

```bash
mvn -f load-tests/tracking-generator/pom.xml package
java -jar load-tests/tracking-generator/target/tracking-generator.jar --active-ratio 0.4 --interval 30s --duration 1h
```

| Escenario | Opciones |
| --- | --- |
| Operativa diaria (soak) | `--profile constant --duration 1h` |
| Picos 3× | `--profile spikes --spike-factor 3 --spike-every 5m --spike-length 1m` |
| Punto de quiebre | `--profile ramp --ramp-step 0.5 --ramp-step-duration 2m --ramp-max 10` |

Otras opciones: `--publishers` (hilos, cada uno con su canal y sus vehiculos; 2 por defecto, entre 1
y 500), `--duplicate-ratio` (0.02: el mismo mensaje publicado dos veces) y `--out-of-order-ratio`
(0.05: el evento sale despues del siguiente del mismo vehiculo). Conexion: `RABBITMQ_HOST`,
`RABBITMQ_PORT`, `RABBITMQ_VHOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_TLS=true` para AMQPS.

Cada 5 s imprime la tasa objetivo, la tasa real enviada y los eventos confirmados por RabbitMQ. Si la
tasa enviada queda por debajo de la objetivo, el limite es el generador: subir `--publishers` o
correrlo desde otra maquina. Cada evento lleva la hora en que se envia (al menos 1 ms despues del
anterior del mismo vehiculo), asi que la hora del servidor desde el que se corre tiene que estar
sincronizada.

## Verificar que no hubo perdida

Al terminar (o con Ctrl+C) imprime los eventos unicos confirmados por RabbitMQ. Ese numero tiene que
coincidir con lo guardado por el central, una vez vaciada la cola `tracking.events`:

```js
db.tracking.countDocuments({licensePlate: /^LT/})
```

Los duplicados no deben sumar: el central deduplica por (matricula, fecha). Entre corridas, borrar
esos eventos (`db.tracking.deleteMany({licensePlate: /^LT/})`) para que el conteo arranque de cero.
