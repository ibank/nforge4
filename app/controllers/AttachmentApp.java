package controllers;

import static play.libs.Json.toJson;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Attachment;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

import org.codehaus.jackson.JsonNode;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import utils.AccessControl;
import utils.HttpUtil;

public class AttachmentApp extends Controller {

    public static Result newFile() throws NoSuchAlgorithmException, IOException {
        // Get the file from request.
        FilePart filePart =
                request().body().asMultipartFormData().getFile("filePath");
        if (filePart == null) {
            return badRequest();
        }
        File file = filePart.getFile();

        // Anonymous cannot upload a file.
        if (UserApp.currentUser() == UserApp.anonymous) {
            return forbidden();
        }

        // Store the file in the user's temporary area.
        Attachment attach = new Attachment();
        boolean isCreated = attach.storeInUserTemporaryArea(UserApp.currentUser().id, file, filePart.getFilename());

        // The request has been fulfilled and resulted in a new resource being
        // created. The newly created resource can be referenced by the URI(s)
        // returned in the entity of the response, with the most specific URI
        // for the resource given by a Location header field.
        // -- RFC 2616, 10.2.2. 201 Created
        String url = routes.AttachmentApp.getFile(attach.id).url();
        response().setHeader("Location", url);

        // The entity format is specified by the media type given in the
        // Content-Type header field. -- RFC 2616, 10.2.2. 201 Created
        // While upload a file using Internet Explorer, if the response is not in
        // text/html, the browser will prompt the user to download it as a file.
        // To avoid this, if application/json is not acceptable by client, the
        // Content-Type field of response is set to "text/html". But, ACTUALLY
        // IT WILL BE SEND IN JSON!
        String contentType = "text/html";
        List<String> accepts = request().accept();
        for (String  accept : accepts) {
            accept = accept.toLowerCase();
            if (accept.equals("application/json") || accept.equals("application/*") || accept.equals("*/*")) {
                contentType = "application/json";
                break;
            }
        }
        response().setHeader("Content-Type", contentType);

        // The response SHOULD include an entity containing a list of resource
        // characteristics and location(s) from which the user or user agent can
        // choose the one most appropriate. -- RFC 2616, 10.2.2. 201 Created
        Map<String, String> fileInfo = new HashMap<String, String>();
        fileInfo.put("id", attach.id.toString());
        fileInfo.put("mimeType", attach.mimeType);
        fileInfo.put("name", attach.name);
        fileInfo.put("url", url);
        fileInfo.put("size", attach.size.toString());
        JsonNode responseBody = toJson(fileInfo);

        if (isCreated) {
            // If an attachment has been created -- it does NOT mean that
            // a file is created in the filesystem -- return 201 Created.
            return created(responseBody);
        } else {
            // If the attachment already exists, return 200 OK.
            // Why not 204? -- Because 204 doesn't allow that response has body,
            // so we cannot tell what is same with the file you try to add.
            return ok(responseBody);
        }
    }

    public static Result getFile(Long id) throws NoSuchAlgorithmException, IOException {
        Attachment attachment = Attachment.findById(id);

        if (attachment == null) {
            return notFound();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), attachment.getContainerAsResource(), Operation.READ)) {
            return forbidden();
        }

        File file = attachment.getFile();

        String filename = HttpUtil.encodeContentDisposition(attachment.name);

        response().setHeader("Content-Length", Long.toString(file.length()));
        response().setHeader("Content-Type", attachment.mimeType);
        response().setHeader("Content-Disposition", "attachment; " + filename);

        return ok(file);
    }

    public static Result deleteFile(Long id)
            throws NoSuchAlgorithmException, IOException {
        // _method must be 'delete'
        Map<String, String[]> data =
                request().body().asMultipartFormData().asFormUrlEncoded();
        if (!HttpUtil.getFirstValueFromQuery(data, "_method").toLowerCase()
                .equals("delete")) {
            return badRequest("_method must be 'delete'.");
        }

        // Remove the attachment.
        Attachment attach = Attachment.findById(id);
        if (attach == null) {
            return notFound();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), attach.getContainerAsResource(), Operation.DELETE)) {
            return forbidden();
        }

        attach.delete();

        if (!Attachment.exists(attach.hash)) {
            if (!Attachment.fileExists(attach.hash)) {
                Logger.error("The uploaded file '" + attach.name + "'cannot be " +
                        "found even if the file is still referred by some" +
                        "attachments.");
            }
            return ok("Both the attachment and its origin file are removed successfully.");
        } else {
            if (Attachment.fileExists(attach.hash)) {
                Logger.warn("The attachment is removed successfully, but its " +
                        "origin file still exists abnormally even if the file " +
                        "referred by nowhere.");
            }
            return ok("The attachment is removed successfully, but its " +
                    "origin file still exists.");
        }
    }

    public static Map<String, String> fileAsMap(Attachment attach) {
        Map<String, String> file = new HashMap<String, String>();

        file.put("id", attach.id.toString());
        file.put("mimeType", attach.mimeType);
        file.put("name", attach.name);
        file.put("url", routes.AttachmentApp.getFile(attach.id).url());
        file.put("size", attach.size.toString());

        return file;
    }

    public static Result getFileList() {
        Map<String, List<Map<String, String>>> files =
                new HashMap<String, List<Map<String, String>>>();

        // Get files from the user's temporary area.
        List<Map<String, String>> tempFiles = new ArrayList<Map<String, String>>();
        for (Attachment attach : Attachment.findTempFiles(UserApp.currentUser().id)) {
            tempFiles.add(fileAsMap(attach));
        }
        files.put("tempFiles", tempFiles);

        // Get attached files only if the user has permission to read it.
        Map<String, String[]> query = request().queryString();
        String containerType = HttpUtil.getFirstValueFromQuery(query, "containerType");
        String containerId = HttpUtil.getFirstValueFromQuery(query, "containerId");

        if (containerType != null && containerId != null) {
            List<Map<String, String>> attachments = new ArrayList<Map<String, String>>();
            for (Attachment attach : Attachment.findByContainer(ResourceType.valueOf(containerType),
                    Long.parseLong(containerId))) {
                if (!AccessControl.isAllowed(UserApp.currentUser(),
                        attach.getContainerAsResource(), Operation.READ)) {
                    return forbidden();
                }
                attachments.add(fileAsMap(attach));
            }
            files.put("attachments", attachments);
        }

        // Return the list of files as JSON.
        response().setHeader("Content-Type", "application/json");
        return ok(toJson(files));
    }
}
