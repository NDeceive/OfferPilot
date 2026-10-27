# Execute after the upstream database initialization. Prints the instance-specific ID.
language = Language.unscoped.find_or_initialize_by(name: 'Java (OpenJDK 17.0.20.1)')
language.update!(source_file: 'Main.java', compile_cmd: '/usr/local/openjdk17/bin/javac Main.java', run_cmd: '/usr/local/openjdk17/bin/java -Xms16m -Xmx64m -XX:MaxMetaspaceSize=32m Main', is_archived: false)
puts language.id
