package com.teemo;

import com.teemo.jgit.JGitUtil;
import org.eclipse.jgit.api.Git;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;


/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) {
        String localPath = "local git repository path";
        JGitUtil.JGitRepositoryBuilder builder = JGitUtil.builder()
                .localPath(localPath)
                .remoteUri("remote git repository path[.git]")
                .username("username")
                .password("password")
                .commitMessage("Update on : " + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        Git git = builder.builder();
        JGitUtil.readMeAppendOnStart(git, "readme title", Arrays.asList("message1", "message2"));
        JGitUtil.rewrite(git, "file path", "messages");
        JGitUtil.commitAndPush(git, builder);
        git.close();    // this is required
    }
}
