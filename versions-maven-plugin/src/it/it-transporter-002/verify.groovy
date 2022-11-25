def project = new XmlSlurper().parse( new File( basedir, 'pom.xml' ) )

assert !( project.dependencies.dependency.find { node -> node.artifactId == 'dummy-api' }.version =~ /3.*/ )
