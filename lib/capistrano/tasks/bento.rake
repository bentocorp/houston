desc "Start Houston app"
task :start_server do
	on roles(:all) do |host|
		upload! "./houston-app/src/main/resources/private-NO-COMMIT.properties", "#{fetch(:deploy_to)}/current/houston-app/src/main/resources/private-NO-COMMIT.properties", :via => :scp
		##execute "cd #{fetch(:deploy_to)}/current/houston-app/target && java -jar houston-app-0.1.0.jar --env=#{fetch(:stage)}"
	end
end