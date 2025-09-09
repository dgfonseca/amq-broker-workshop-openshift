## Ejercicio 3: Configuración de clientes

En este ejercicio se configurará **TLS** para exponer el broker fuera del cluster de OpenShift, asegurando que las conexiones externas sean seguras.

---

### Paso 1: Configuración de certificados TLS

Para habilitar TLS en el broker, primero se deben generar los certificados necesarios y crear un secreto en OpenShift.

#### 1.1 Crear keystore del broker

```bash
keytool -genkey -alias broker -keyalg RSA -keystore ./broker.ks
```

> **Nota:** El **CN (Common Name)** que se configure en el certificado debe ser el mismo dominio de la **ruta del broker en OpenShift**, de lo contrario, las conexiones TLS externas fallarán por validación de hostname.

#### 1.2 Exportar el certificado del broker

```bash
keytool -export -alias broker -keystore ./broker.ks -file ./broker_cert.pem
```

#### 1.3 Importar el certificado en el truststore del cliente

```bash
keytool -import -alias broker -keystore ./client.ts -file ./broker_cert.pem
```

#### 1.4 Obtener el certificado de la cadena de confianza de OpenShift

```bash
openssl s_client -showcerts -servername console-openshift-console.apps.rosa.rosa-crb9w.br05.p3.openshiftapps.com \
-connect console-openshift-console.apps.rosa.rosa-crb9w.br05.p3.openshiftapps.com:443 \
> cert_chain.pem
```

#### 1.5 Importar el certificado de la cadena en el truststore

```bash
keytool -importcert -alias my-cert -file cert_chain.pem -keystore client.ts -storepass 123456789 -trustcacerts
```

#### 1.6 Crear el secreto en OpenShift con keystore y truststore

```bash
oc create secret generic my-tls-secret \
--from-file=broker.ks=./broker.ks \
--from-file=client.ts=./client.ts \
--from-literal=keyStorePassword=123456789 \
--from-literal=trustStorePassword=123456789
```

#### Paso 1.7: Configuración de JAAS con archivos de propiedades

En este paso se configurará la autenticación de usuarios en AMQ utilizando **JAAS** a través de archivos de propiedades.  

#### 1.8 Archivos requeridos

1. **login.config**  
   Define el origen de los archivos de usuarios y roles que serán utilizados para la autenticación y autorización.  
   Ejemplo:  
   ```properties
   activemq {
       org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule required
       org.apache.activemq.jaas.properties.user="new-users.properties"
       org.apache.activemq.jaas.properties.role="new-roles.properties";
   };
   ```

2. **new-users.properties**  
   Contiene la lista de usuarios y contraseñas en formato `llave=valor`.  
   ```properties
    amq-client=r3dh4t_cl13n7
   ```

3. **new-roles.properties**  
   Define los roles lógicos asociados a cada usuario en formato `llave=valor`.  
   ```properties
   client=amq-client
   ```

#### 1.9 Creación del Secret en OpenShift

Se deben empaquetar los tres archivos en un Secret de OpenShift:  

```bash
oc create secret generic custom-jaas-config \
  --from-file=login.config \
  --from-file=new-users.properties \
  --from-file=new-roles.properties
```

#### 1.10 Modificación del Broker

Para vincular el Secret al broker desplegado, se debe editar el recurso `ActiveMQArtemis` y agregar la siguiente sección en el apartado `spec.deploymentPlan.extraMounts`:  

```yaml
spec:
  deploymentPlan:
    extraMounts:
      secrets:
        - "custom-jaas-config"
```

#### 1.11 Configuración de permisos en `brokerProperties`

Además de los archivos de usuarios y roles, se deben definir permisos específicos sobre las acciones que cada rol puede realizar dentro del broker.  
Esto se hace agregando las siguientes entradas en `spec.brokerProperties`:  

```yaml
spec:
  brokerProperties:
    - securityRoles.#.client.send=true
    - securityRoles.#.client.consume=true
    - securityRoles.#.client.createAddress=false
    - securityRoles.#.client.deleteAddress=false
    - securityRoles.#.client.manage=false
    - securityRoles.#.client.createNonDurableQueue=false
    - securityRoles.#.client.browse=true
```

✅ **Resultado esperado:**  
- Los usuarios definidos en `new-users.properties` y sus roles en `new-roles.properties` serán autenticados vía JAAS.  
- Los permisos en `brokerProperties` controlarán qué operaciones puede realizar cada rol, garantizando seguridad y control granular sobre las colas y tópicos.  

---

### Paso 2: Exponer el broker con TLS

En el manifiesto del broker (`3_client_amq.yaml`), se debe referenciar el secreto `my-tls-secret` para que los **acceptors** usen TLS y el broker quede expuesto de manera segura fuera del cluster.

```bash
oc create namespace amq-ej3-nombre
oc apply -f 3_client_amq.yaml
```

---

### Paso 3: Clientes productor y consumidor en Quarkus

En este paso se desarrollarán dos aplicaciones en Quarkus:  

- **producer-amq** → Aplicación encargada de enviar mensajes a la cola del broker AMQ.  
- **consumer-amq** → Aplicación encargada de consumir los mensajes desde la cola del broker AMQ.  

Ambos proyectos utilizan la extensión **quarkus-openshift**, lo que permite desplegarlos de manera rápida en el cluster de OpenShift.  

#### 3.1 Requisitos previos
- Tener Java 21 y maven instalados.
- Haber configurado correctamente TLS en el broker (pasos anteriores).  
- Estar autenticado en OpenShift CLI con:  

```bash
oc login <cluster-api-url> --token=<your-token>
oc project <nombre-del-proyecto>
```

#### 3.2 Despliegue de los proyectos

Dentro del directorio de cada aplicación, ejecutar el siguiente comando:  

```bash
mvn clean package
```

Este comando generará el build y desplegará automáticamente la aplicación en el proyecto de OpenShift actual.  

- Ejecutar en `producer-amq` para desplegar la aplicación productora.  
- Ejecutar en `consumer-amq` para desplegar la aplicación consumidora.  

#### 3.3 Validación

- Confirmar en la consola de OpenShift que los pods de **producer-amq** y **consumer-amq** se encuentran en estado *Running*.  
- Verificar en los logs del **consumer-amq** que los mensajes enviados desde **producer-amq** llegan correctamente a la cola configurada en AMQ.

### Paso 4: Pruebas

En este paso se validará el comportamiento de las colas configuradas en AMQ utilizando los endpoints expuestos por el **producer-amq**.  

#### 4.1 Envío de mensaje a cola *Anycast* (SHIPMENTS)

Ejecutar el siguiente comando `curl` para enviar un mensaje a la cola `SHIPMENTS` configurada como **Anycast**:  

```bash
curl --location 'http://producer-amq-amq-ej3-nombre.apps.rosa.rosa-crb9w.br05.p3.openshiftapps.com/hello' \
--header 'Content-Type: application/json' \
--data '{
    "city":"Bogota",
    "address":"CRA 7ma",
    "clientId":"123123"
}'
```

✅ **Resultado esperado:**  
El mensaje debe ser consumido por **una sola réplica** de las aplicaciones consumidoras (de las 3 instancias desplegadas).  

---

#### 4.2 Envío de mensaje a tópico *Multicast* (TAXES)

Ejecutar el siguiente comando `curl` para enviar un mensaje al tópico `TAXES` configurado como **Multicast**:  

```bash
curl --location 'http://producer-amq-amq-ej3-nombre.apps.rosa.rosa-crb9w.br05.p3.openshiftapps.com/hello/tax' \
--header 'Content-Type: application/json' \
--data '{
    "userName":"David Fonseca",
    "iva":"20%",
    "amount":"90000"
}'
```

✅ **Resultado esperado:**  
El mensaje debe ser recibido de forma **simultánea por las 3 réplicas** de las aplicaciones consumidoras desplegadas.  

---

#### 4.3 Validación

- Revisar los **logs de los consumidores** en OpenShift.  
- En la prueba Anycast se espera que solo uno de los pods muestre el mensaje recibido.  
- En la prueba Multicast, los tres pods deben reflejar el mensaje recibido en sus logs.  

### Paso 5: Prueba con aplicación productora TLS (tls-producer-amq)

En este paso se validará la conectividad **externa** al broker utilizando una aplicación productora llamada **tls-producer-amq**, la cual se ejecutará desde la máquina local.  

Esta aplicación hace uso del *address* expuesto en OpenShift junto con los certificados generados previamente en los pasos de configuración de TLS.  

#### 5.1 Configuración requerida

Antes de ejecutar el proyecto, es necesario modificar las siguientes variables en el archivo `application.properties` para que apunten al `client.ts` generado anteriormente y utilicen la contraseña configurada durante la creación del keystore/truststore:  

```properties
quarkus.tls.test.trust-store.jks.path=/Users/davidfonseca/Desktop/workshops/amq/amq-broker-workshop-openshift/ejercicio_3/client.ts
quarkus.tls.test.trust-store.jks.password=123456789
```

> 🔑 **Nota importante:**  
> - La ruta debe ser ajustada de acuerdo a la ubicación del archivo `client.ts` en tu máquina local.  
> - El valor de `password` debe coincidir con el utilizado al generar el truststore en los pasos iniciales.  
> - Asegúrate de usar el mismo **CN** que corresponde al dominio de la ruta de OpenShift del broker (validado en el Paso 1).  

#### 5.2 Ejecución

Con la configuración realizada, se puede ejecutar la aplicación desde el directorio de **tls-producer-amq** utilizando:

```bash
mvn clean quarkus:dev
```

#### 5.3 Validación con `curl`

Una vez levantada la aplicación, también se puede validar su funcionamiento usando el mismo comando `curl` de pruebas presentado en el **Paso 4**, pero cambiando el dominio expuesto en OpenShift por `http://localhost:8080`.  

Ejemplo para enviar un mensaje Anycast a `SHIPMENTS`:  

```bash
curl --location 'http://localhost:8080/hello' \
--header 'Content-Type: application/json' \
--data '{
    "city":"Bogota",
    "address":"CRA 7ma",
    "clientId":"123123"
}'
```

Ejemplo para enviar un mensaje Multicast a `TAXES`:  

```bash
curl --location 'http://localhost:8080/hello/tax' \
--header 'Content-Type: application/json' \
--data '{
    "userName":"David Fonseca",
    "iva":"20%",
    "amount":"90000"
}'
```

✅ Esto permite probar la comunicación TLS con el broker desde la máquina local.  


### Archivos necesarios

- `3_tls_amq.yaml` : Manifiesto de despliegue del broker con TLS habilitado.  
- `broker.ks`, `broker_cert.pem`, `client.ts`, `cert_chain.pem` : Archivos de certificados generados.  
