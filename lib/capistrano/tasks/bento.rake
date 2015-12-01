desc "Build Houston"
task :build do
	on roles(:all) do |host|
		upload! "./houston-app/src/main/resources/private-NO-COMMIT.conf", "#{fetch(:deploy_to)}/current/houston-app/src/main/resources/private-NO-COMMIT.conf", :via => :scp
		execute "cd #{fetch(:deploy_to)}/current && mvn clean install"
	end
end

desc "Start Houston"
task :start do
	on roles(:all) do |host|
		# Make sure to "cd /sites/houston/current" so log files are not written to home directory
		execute "cd #{fetch(:deploy_to)}/current; nohup java -jar -Dserver.port=8080 houston-app/target/houston-app-0.1.0.jar --env=#{fetch(:stage)} >/dev/null 2>&1 &"
		#execute "java -jar -Dserver.port=8081 #{fetch(:deploy_to)}/current/houston-app/target/houston-app-0.1.0.jar --env=#{fetch(:stage)} --flush-redis"
	end
end

desc "Stop Houston"
task :stop do
	on roles(:all) do |host|
		execute "ps -e -o pid,cmd | grep -oP '^\s*[0-9]+(?=\sjava \-jar \-Dserver\.port\=8080)' | sed -e 's/\s\+//g' | xargs kill -9"
	end
end

desc "Restart Houston"
task :restart do
	on roles(:all) do |host|
		invoke 'stop'
		invoke 'start'
	end
end
