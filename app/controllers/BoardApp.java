/**
 * @author Ahn Hyeok Jun
 */

package controllers;

import com.avaje.ebean.Page;

import models.Attachment;
import models.Comment;
import models.Post;
import models.Project;
import models.User;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.Constants;
import views.html.board.editPost;
import views.html.board.newPost;
import views.html.board.postList;

public class BoardApp extends Controller {

    //TODO 이 클래스는 원래 따로 존재해야 함.
    public static class SearchCondition{
        public final static String ORDERING_KEY_ID = "id";
        public final static String ORDERING_KEY_TITLE = "title";
        public final static String ORDERING_KEY_AGE = "date";
        public final static String ORDERING_KEY_AUTHOR = "authorName";

        public SearchCondition() {
            this.order = Direction.DESC.direction();
            this.key = ORDERING_KEY_ID;
            this.filter = "";
            this.pageNum = 1;
        }

        public String order;
        public String key;
        public String filter;
        public int pageNum;
    }


    public static Result posts(String userName, String projectName, int pageNum) {
        Form<SearchCondition> postParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition postSearchCondition = postParamForm.bindFromRequest().get();
        postSearchCondition.pageNum = pageNum - 1;
        Project project = ProjectApp.getProject(userName, projectName);

        if (!AccessControl.isCreatable(User.findByLoginId(session().get("loginId")), project, ResourceType.BOARD_POST)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        Page<Post> posts = Post.findOnePage(project.owner, project.name, postSearchCondition.pageNum,
                        Direction.getValue(postSearchCondition.order), postSearchCondition.key, postSearchCondition.filter);
        return ok(postList.render("menu.board", project, posts, postSearchCondition));
    }

    public static Result newPostForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if (UserApp.currentUser() == UserApp.anonymous) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        return ok(newPost.render("board.post.new", new Form<Post>(Post.class), project));
    }

    public static Result newPost(String userName, String projectName) {
        Form<Post> postForm = new Form<Post>(Post.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        if (postForm.hasErrors()) {
            flash(Constants.WARNING, "board.post.empty");

            return redirect(routes.BoardApp.newPost(userName, projectName));
        } else {
            Post post = postForm.get();
            post.authorId = UserApp.currentUser().id;
            post.authorLoginId = UserApp.currentUser().loginId;
            post.authorName = UserApp.currentUser().name;
            post.commentCount = 0;
            post.project = project;
            Post.write(post);

            // Attach all of the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, ResourceType.BOARD_POST, post.id);
        }

        return redirect(routes.BoardApp.posts(project.owner, project.name, 1));
    }

    public static Result post(String userName, String projectName, Long postId) {
        Post post = Post.findById(postId);
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isCreatable(User.findByLoginId(session().get("loginId")), project, ResourceType.BOARD_POST)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        if (post == null) {
            flash(Constants.WARNING, "board.post.notExist");
            return redirect(routes.BoardApp.posts(project.owner, project.name, 1));
        } else {
            Form<Comment> commentForm = new Form<Comment>(Comment.class);
            return ok(views.html.board.post.render(post, commentForm, project));
        }
    }

    public static Result newComment(String userName, String projectName, Long postId) {
        Form<Comment> commentForm = new Form<Comment>(Comment.class).bindFromRequest();

        Project project = ProjectApp.getProject(userName, projectName);
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "board.comment.empty");
            return redirect(routes.BoardApp.post(project.owner, project.name, postId));
        } else {
            Comment comment = commentForm.get();
            comment.post = Post.findById(postId);
            comment.authorId = UserApp.currentUser().id;
            comment.authorLoginId = UserApp.currentUser().loginId;
            comment.authorName = UserApp.currentUser().name;

            Comment.write(comment);
            Post.countUpCommentCounter(postId);

            // Attach all of the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, ResourceType.BOARD_COMMENT, comment.id);

            return redirect(routes.BoardApp.post(project.owner, project.name, postId));
        }
    }

    public static Result deletePost(String userName, String projectName, Long postId) {
        Project project = ProjectApp.getProject(userName, projectName);
        Post.delete(postId);
        Attachment.deleteAll(ResourceType.BOARD_POST, postId);
        return redirect(routes.BoardApp.posts(project.owner, project.name, 1));
    }

    public static Result editPostForm(String userName, String projectName, Long postId) {
        Post existPost = Post.findById(postId);
        Form<Post> editForm = new Form<Post>(Post.class).fill(existPost);
        Project project = ProjectApp.getProject(userName, projectName);

        if (AccessControl.isAllowed(UserApp.currentUser(), existPost.asResource(), Operation.UPDATE)) {
            return ok(editPost.render("board.post.modify", editForm, postId, project));
        } else {
            flash(Constants.WARNING, "board.notAuthor");
            return redirect(routes.BoardApp.post(project.owner, project.name, postId));
        }
    }

    public static Result editPost(String userName, String projectName, Long postId) {
        Form<Post> postForm = new Form<Post>(Post.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);

        if (postForm.hasErrors()) {
            flash(Constants.WARNING, "board.post.empty");
            return redirect(routes.BoardApp.editPost(userName, projectName, postId));
        } else {

            Post post = postForm.get();
            post.authorId = UserApp.currentUser().id;
            post.authorName = UserApp.currentUser().name;
            post.id = postId;
            post.project = project;

            Post.edit(post);

            // Attach the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, ResourceType.BOARD_POST, post.id);
        }

        return redirect(routes.BoardApp.posts(project.owner, project.name, 1));

    }

    public static Result deleteComment(String userName, String projectName, Long postId, Long commentId) {
        Comment.delete(commentId);
        Post.countDownCommentCounter(postId);
        Attachment.deleteAll(ResourceType.BOARD_COMMENT, commentId);
        return redirect(routes.BoardApp.post(userName, projectName, postId));
    }

}
