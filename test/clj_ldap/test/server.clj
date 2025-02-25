(ns clj-ldap.test.server
  "An embedded ldap server for unit testing"
  (:require [clj-ldap.client :as ldap])
  (:import (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.util HashSet)
           (org.apache.directory.server.constants ServerDNConstants)
           (org.apache.directory.server.core DefaultDirectoryService DirectoryService)
           (org.apache.directory.server.core.partition.impl.btree.jdbm JdbmIndex JdbmPartition)
           (org.apache.directory.server.core.partition.ldif LdifPartition)
           (org.apache.directory.server.ldap LdapServer)
           (org.apache.directory.server.protocol.shared.transport TcpTransport)
           (org.apache.directory.shared.ldap.schema.ldif.extractor.impl DefaultSchemaLdifExtractor)
           (org.apache.directory.shared.ldap.schema.loader.ldif LdifSchemaLoader)
           (org.apache.directory.shared.ldap.schema.manager.impl DefaultSchemaManager)))

(defonce server (atom nil))

(defn- override-java-version!
  "Override the java.version property as the ancient version of
  directory-server we use for tests seems not to understand the concept of a
  version number with multiple digits (ie. 11 or 14). This version isn't
  actually used anywhere, just parsed as a side effect of loading a SystemUtils
  class, so it only needs to appear valid."
  []
  (System/setProperty "java.version" "0.0.0"))

(defn- add-partition!
  "Adds a partition to the embedded directory service"
  [^DirectoryService service ^String id dn]
  (let [partition (doto (JdbmPartition.)
                    (.setId id)
                    (.setPartitionDir (File. (.getWorkingDirectory service) id))
                    (.setSuffix dn))]
    (.addPartition service partition)
    partition))

(defn- add-index!
  "Adds an index to the given partition on the given attributes"
  [^JdbmPartition partition & attrs]
  (let [indexed-attrs (HashSet.)]
    (doseq [attr attrs]
      (.add indexed-attrs (JdbmIndex. attr)))
    (.setIndexedAttributes partition indexed-attrs)))

(defn start-ldap-server
  "Start an embedded ldap server"
  [port]
  (override-java-version!)
  (let [work-dir (Files/createTempDirectory "ldap" (into-array FileAttribute []))
        _ (.deleteOnExit (.toFile work-dir))
        schema-dir (.resolve work-dir "schema")
        _ (Files/createDirectory schema-dir (into-array FileAttribute []))
        ;; Setup steps based on http://svn.apache.org/repos/asf/directory/documentation/samples/trunk/embedded-sample/src/main/java/org/apache/directory/seserver/EmbeddedADSVer157.java
        ^DirectoryService directory-service (doto (DefaultDirectoryService.)
                                              (.setShutdownHookEnabled true)
                                              (.setWorkingDirectory (.toFile work-dir)))
        schema-partition (.getSchemaPartition (.getSchemaService directory-service))
        ldif-partition (doto (LdifPartition.)
                        (.setWorkingDirectory (.toString schema-dir)))
        _extractor (doto (DefaultSchemaLdifExtractor. (.toFile work-dir))
                     (.extractOrCopy true))
        _ (.setWrappedPartition schema-partition ldif-partition)
        schema-manager (DefaultSchemaManager. (LdifSchemaLoader. (.toFile schema-dir)))
        _ (.setSchemaManager directory-service schema-manager)
        _ (.loadAllEnabled schema-manager)
        _ (.setSchemaManager schema-partition schema-manager)
        ldap-transport (TcpTransport. port)
        ldap-server (doto (LdapServer.)
                      (.setDirectoryService directory-service)
                      (.setAllowAnonymousAccess true)
                      (.setTransports
                        (into-array [ldap-transport])))]
    (->> (add-partition! directory-service "system" (ServerDNConstants/SYSTEM_DN))
         (.setSystemPartition directory-service))
    (-> (add-partition! directory-service
                        "clojure" "dc=alienscience,dc=org,dc=uk")
        (add-index! "objectClass" "ou" "uid"))
    (.startup directory-service)
    (.start ldap-server)
    [directory-service ldap-server]))

(defn- add-toplevel-objects!
  "Adds top level objects, needed for testing, to the ldap server"
  [connection]
  (ldap/add connection "dc=alienscience,dc=org,dc=uk"
            {:objectClass ["top" "domain" "extensibleObject"]
             :dc "alienscience"})
  (ldap/add connection "ou=people,dc=alienscience,dc=org,dc=uk"
            {:objectClass ["top" "organizationalUnit"]
             :ou "people"})
  (ldap/add connection
            "cn=Saul Hazledine,ou=people,dc=alienscience,dc=org,dc=uk"
            {:objectClass ["top" "Person"]
             :cn "Saul Hazledine"
             :sn "Hazledine"
             :description "Creator of bugs"}))

(defn stop!
  "Stops the embedded ldap server"
  []
  (when @server
    (try
      (let [[^DirectoryService directory-service ldap-server] @server]
        (reset! server nil)
        (.stop ^LdapServer ldap-server)
        (.shutdown directory-service))
      (catch Exception _e))))

(defn start!
  "Starts an embedded ldap server on the given port"
  [port]
  (stop!)
  (reset! server (start-ldap-server port))
  (let [conn (ldap/connect {:host {:address "localhost" :port port}})]
    (add-toplevel-objects! conn)))
