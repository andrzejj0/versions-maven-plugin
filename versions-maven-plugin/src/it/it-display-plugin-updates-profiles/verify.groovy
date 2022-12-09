output = new File(basedir, "output.txt").text
assert output =~ /\Qlocalhost:dummy-maven-plugin\E\s*\.*\s*1\.0\s+->\s+3\.0\b/
