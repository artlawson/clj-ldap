(defproject puppetlabs/clj-ldap "0.4.1-SNAPSHOT"
  :description "Clojure ldap client (Puppet Labs's fork)."
  :url "https://github.com/puppetlabs/clj-ldap"
  :dependencies [[org.clojure/clojure]
                 [com.unboundid/unboundid-ldapsdk "6.0.10"]
                 [org.clojure/tools.logging]]
  :parent-project {:coords [puppetlabs/clj-parent "7.2.7"]
                   :inherit [:managed-dependencies]} 
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :profiles {:dev {:dependencies [[org.apache.directory.server/apacheds-all "1.5.7"
                                   ;; This dependency causes the classpath to contain two copies of the schema,
                                   ;; which prevents the test Directory Service from starting
                                   :exclusions [org.apache.directory.shared/shared-ldap-schema]]
                                  [org.slf4j/slf4j-simple "1.5.10"]]}}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]
  :plugins [[jonase/eastwood "1.2.2" :exclusions [org.clojure/clojure]]
            [lein-parent "0.3.7"]]
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"})
