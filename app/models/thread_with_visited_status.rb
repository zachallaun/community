class ThreadWithVisitedStatus < ActiveRecord::Base
  self.table_name = 'threads_with_visited_status'

  include PostgresView
  include DiscussionThreadCommon

  scope :for_user, ->(user) { where("user_id = ? OR user_id IS NULL", user.id) }

  def unread?
    last_visited.nil? || marked_unread_at > last_visited
  end
end