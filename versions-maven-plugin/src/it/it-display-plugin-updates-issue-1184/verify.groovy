def output = new File( basedir, "output.txt").text
assert !(output =~ /\Qmaven-git-versioning-extension\E/)

