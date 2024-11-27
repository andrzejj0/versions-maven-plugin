pom = new File( basedir, "pom.xml" ).text

assert !pom.contains('SNAPSHOT')
