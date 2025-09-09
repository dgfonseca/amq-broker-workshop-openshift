## Ejercicio 2: Habilitar Alta Disponibilidad (HA) con dos réplicas

En este ejercicio se desplegará un broker AMQ en modo **Alta Disponibilidad (HA)** con dos réplicas en OpenShift. El objetivo es comprobar la sincronización de mensajes entre brokers cuando uno de ellos es escalado.

---

### Paso 1: Crear el namespace

Cree un nuevo proyecto en OpenShift para este ejercicio:

```bash
oc new-project amq-ej2-nombre
```

---

### Paso 2: Aplicar el manifiesto de despliegue en HA

Aplique el archivo `2_ha_amq.yaml` para desplegar un broker con dos réplicas:

```bash
oc apply -f 2_ha_amq.yaml -n amq-ej2-nombre
```

Verifique que ambos pods estén en estado `Running`:

```bash
oc get pods -n amq-ej2-nombre
```

### Configuraciones importantes del broker (2_ha_amq.yaml)

A continuación se destacan las configuraciones más relevantes que permiten habilitar Alta Disponibilidad (HA) en el broker:

1. **Console**  
   - `console.expose: true` expone la consola web de administración del broker, permitiendo acceder a la UI para monitorear colas, mensajes y configuraciones.

2. **Acceptors**  
   - Define los puntos de conexión (`61616`) y protocolos soportados (`amqp`, `openwire`, `core`).  
   - `expose: true` permite que este puerto sea accesible desde fuera del cluster (útil para clientes externos).

3. **Credenciales de administrador**  
   - `adminUser: admin` y `adminPassword: redhat` definen las credenciales para acceder tanto a la consola web como al CLI del broker.

4. **brokerProperties**  
   - Incluyen configuraciones para habilitar métricas (`jvmGc`, `jvmThread`).  
   - Definen dos colas configuradas:  
     - **SHIPMENTS** con `ANYCAST` (mensajes enviados a un solo consumidor).  
     - **TAXES** con `MULTICAST` (mensajes entregados a múltiples consumidores).

5. **Variables de entorno (env)**  
   - `JAVA_ARGS_APPEND` ajusta el uso de memoria de la JVM para optimizar el consumo de recursos en OpenShift.

6. **deploymentPlan**  
   - `size: 2` despliega **dos réplicas** del broker, activando un escenario de Alta Disponibilidad (HA).  
   - `persistenceEnabled: true` garantiza que los mensajes se almacenen en disco, evitando pérdidas si un pod falla.  
   - `messageMigration: true` habilita la **migración automática de mensajes** entre réplicas cuando un broker es escalado o reiniciado.  
   - Configuración de probes (`readinessProbe`, `livenessProbe`, `startupProbe`) asegura que OpenShift supervise el estado de los pods.  
   - `storage.size: 5Gi` define el volumen persistente para almacenar datos del broker.  
   - `journalType: nio` indica que se usará almacenamiento basado en **NIO (Non-blocking I/O)**.

7. **Version**  
   - `7.13` especifica la versión de AMQ Broker a desplegar.

---

> Lo más importante en este YAML es la combinación de `size: 2` y `messageMigration: true`, ya que juntas permiten probar el escenario de HA, asegurando que los mensajes se sincronicen entre brokers activos y que se mantenga la continuidad del servicio.


---

### Paso 3: Enviar un mensaje al broker B

Acceda al pod correspondiente al **broker B** y ejecute el siguiente comando con el Artemis CLI para enviar un mensaje a la cola `SHIPMENTS`:

```bash
./amq-broker/bin/artemis producer \
  --url tcp://broker-amq-artemis-1-svc:61616 \
  --user admin \
  --password redhat \
  --destination queue://SHIPMENTS \
  --message-count 1 \
  --message "Mensaje desde broker B"
```

---

### Paso 4: Validar mensajes en la consola

- Acceda a la consola web de administración del broker. Routes > broker-amq-wconsj-#-svc-rte 
- Confirme que el mensaje aparece en el **broker 2 (1)**.  
- Verifique que en el **broker 1 (0)** no existe el mensaje.

---

### Paso 5: Escalar a una réplica

Reduzca el número de réplicas a 1 para que OpenShift gestione la sincronización:

Installed Operators > Red Hat Integration - AMQ Broker for RHEL 9 (Multiarch) > ActiveMQArtemis > Current Namespace Only > broker-amq > YAML.

Editar el campo spec.deploymentPlan.size: 1


Esto provocará que el **Scaler** sincronice los mensajes. Los mensajes almacenados en el **broker 1** serán transferidos al **broker 0**.

---

### Paso 6: Validar el comportamiento

- Acceda nuevamente a la consola del broker activo.  
- Verifique que los mensajes previamente enviados al **broker 1** ahora se encuentran disponibles en el **broker 0**.  
- Este comportamiento valida el funcionamiento del **HA** y la correcta sincronización de mensajes entre réplicas.

---

### Archivos necesarios

- `2_ha_amq.yaml` : Manifiesto de despliegue del broker AMQ con dos réplicas en HA.
