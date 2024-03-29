# config valid only for current version of Capistrano
lock '>=3.3.5'

set :application, 'houston'
set :repo_url, 'git@github.com:bentocorp/houston.git'

# Default branch is :master
ask :branch, `git rev-parse --abbrev-ref HEAD`.chomp

# Default deploy_to directory is /var/www/my_app_name
set :deploy_to, '/sites/houston'

# Default value for :scm is :git
# set :scm, :git

# Default value for :format is :pretty
# set :format, :pretty

# Default value for :log_level is :debug
# set :log_level, :debug

# Default value for :pty is false
# set :pty, true

# Default value for :linked_files is []
# set :linked_files, fetch(:linked_files, []).push('config/database.yml', 'config/secrets.yml')

# Default value for linked_dirs is []
# set :linked_dirs, fetch(:linked_dirs, []).push('log', 'tmp/pids', 'tmp/cache', 'tmp/sockets', 'vendor/bundle', 'public/system')

# Default value for default_env is {}
# set :default_env, { path: "/opt/ruby/bin:$PATH" }

# Default value for keep_releases is 5
# set :keep_releases, 5

require 'securerandom'

namespace :deploy do

  after :finished, :chmod_775 do
    on roles(:all) do |host|
        execute "sudo chmod -R 775 /sites/houston"
      end
  end

  after :chmod_775, :cold_start do
    set :deploy_id, SecureRandom.uuid
    run_locally do
      execute "echo '>> Set deploy_id=#{fetch(:deploy_id)}'"
    end
    on roles(:all) do |host|
      invoke 'stop'
      invoke 'build'
      invoke 'start'
    end
  end

  after :restart, :clear_cache do
    on roles(:web), in: :groups, limit: 3, wait: 10 do
      # Here we can do anything such as:
      # within release_path do
      #   execute :rake, 'cache:clear'
      # end
    end
  end

end
