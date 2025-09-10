# Workshop Broker AMQ en OpenShift

Este workshop tiene como objetivo guiar a los participantes en el despliegue y configuración de un broker **Red Hat AMQ** en un cluster de OpenShift. Se realizarán ejercicios prácticos paso a paso.

---

## Ejercicio 1: Despliegue básico de un broker AMQ

En este ejercicio se desplegará un broker AMQ simple en OpenShift usando un manifiesto YAML predefinido.

### Paso 1: Crear el namespace

Cree un nuevo namespace en OpenShift con el siguiente comando, reemplazando `amq-ej1-nombre` por el nombre que desee asignar:

```bash
oc new-project amq-ej1-nombre
```

> Nota: El namespace servirá como espacio aislado para desplegar los recursos del broker.

---

### Paso 2: Aplicar el manifiesto YAML del broker

Aplique el archivo `1_simple_amq.yaml` dentro del namespace creado:

```bash
oc apply -f 1_simple_amq.yaml -n amq-ej1-nombre
```

Verifique que el broker se haya desplegado correctamente:

```bash
oc get pods -n amq-ej1-nombre
```

> Debería ver un pod con el broker AMQ en estado `Running`.

---

## Configuraciones importantes del broker (1_simple_amq.yaml)

A continuación se describen las configuraciones más relevantes incluidas en el YAML:

1. **Acceptors**  
   - Define los puntos de conexión del broker.  
   - En este YAML: puerto `61616`, soporta protocolos `amqp`, `openwire` y `core`.  
   - `supportAdvisory` y `suppressInternalManagementObjects` controlan la generación de mensajes de administración internos.

2. **Credenciales de administrador**  
   - `adminUser` y `adminPassword` se usan para acceder al broker y administrar colas y direcciones.

3. **brokerProperties**  
   - Configuraciones internas del broker, incluyendo métricas de JVM (`jvmGc` y `jvmThread`).  
   - Configuración de direcciones y colas (`SHIPMENTS` y `TAXES`) con tipos de enrutamiento `ANYCAST` y `MULTICAST`.

4. **Variables de entorno (env)**  
   - `JAVA_ARGS_APPEND`: se ajusta la memoria máxima de la JVM (50% de la RAM disponible).

5. **deploymentPlan**  
   - Define el tamaño del broker (`size: 1`), persistencia de datos (`persistenceEnabled: true`) y políticas de seguridad.  
   - Configuración de recursos para el pod (`cpu` y `memory`), probes de salud (`readinessProbe`, `livenessProbe`, `startupProbe`).  
   - `journalType: nio` indica el tipo de almacenamiento interno para mensajes.  
   - `storage.size` define 5Gi para almacenamiento persistente.

6. **Version**  
   - Especifica la versión del broker AMQ Artemis a desplegar (`7.13`).

> Estas configuraciones permiten que el broker esté listo para recibir conexiones, administrar colas y métricas, y garantizar persistencia y monitoreo de su estado.

---

## Paso 3: Enviar mensajes a la cola SHIPMENTS

Una vez que el broker está desplegado y en estado `Running`, se puede acceder al pod y enviar mensajes a la cola.

### 3.1 Acceder al pod del broker

Listar los pods del namespace:

```bash
oc get pods -n amq-ej1-nombre
```

Acceder a la terminal del pod:

```bash
oc exec -it <nombre-del-pod> -n amq-ej1-nombre -- bash
```

> Reemplace `<nombre-del-pod>` por el nombre real del pod mostrado en el comando anterior.

---

### 3.2 Enviar un mensaje a la cola

Dentro del pod, ejecute el siguiente comando para enviar un mensaje a la cola **SHIPMENTS**:

```bash
CURRENT_DATE=$(date)
./amq-broker/bin/artemis producer \
  --url tcp://broker-amq-artemis-0-svc:61616 \
  --user admin \
  --password redhat \
  --destination queue://SHIPMENTS \
  --message-count 10 \
  --message "Hello, this is an echo message $CURRENT_DATE"
```


> Este paso permite validar que la cola **SHIPMENTS** está funcionando y que el broker puede recibir mensajes correctamente.

## Paso 4: Consumir mensajes de la cola SHIPMENTS

Después de enviar mensajes al broker, podemos consumirlos desde la misma cola para validar la entrega.

### 4.1 Acceder al pod del broker

Si no estás dentro del pod, primero accede nuevamente a la terminal del pod:

```bash
oc get pods -n amq-ej1-nombre
oc exec -it <nombre-del-pod> -n amq-ej1-nombre -- bash
```

> Reemplace `<nombre-del-pod>` por el nombre real del pod.

---

### 4.2 Consumir mensajes de la cola

Dentro del pod, ejecute el siguiente comando para consumir mensajes de la cola **SHIPMENTS**:

```bash
./amq-broker/bin/artemis consumer \
  --url tcp://broker-amq-artemis-0-svc:61616 \
  --user admin \
  --password redhat \
  --destination queue://SHIPMENTS \
  --message-count 10 \
  --verbose
```

> Este comando mostrará en pantalla el contenido del mensaje enviado previamente, permitiendo verificar que la cola funciona correctamente y que los mensajes se entregan.


### Archivos necesarios

- `1_simple_amq.yaml` : Manifiesto básico de despliegue del broker AMQ.
