task :default => :build

MOD_ID   = "ZBLuaPerfMon"
MOD_TYPE = "client"
VERSIONS = {
  "42" => "17",
}

VERSIONS.each do |ver, jdk_ver|
  desc "build for #{ver}"
  task "build:#{ver}" do
    Dir.chdir("java") do
      env = {
        "JAVA_HOME" => "/Library/Java/JavaVirtualMachines/openjdk-#{jdk_ver}.jdk/Contents/Home"
      }
      sh env, "gradle build -PZVersion=#{ver}"
    end
    dst_dir = "#{ver}/media/java/#{MOD_TYPE}"
    FileUtils.mkdir_p dst_dir
    FileUtils.mv "java/build/libs/#{MOD_ID}-#{ver}.jar", "#{dst_dir}/#{MOD_ID}.jar"
  end
end

desc "build all"
task :build => VERSIONS.keys.map { |ver| "build:#{ver}" }
