def output = new File(basedir, "output.txt").text
assert output =~ /\Qlocalhost:issue-114-artifact\E\s*\.*\s*\Q1.0\E\s+->/
