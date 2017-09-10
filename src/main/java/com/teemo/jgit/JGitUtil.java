package com.teemo.jgit;

import com.fasterxml.jackson.dataformat.yaml.UTF8Writer;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

/**
 * JGitUtil
 *
 * @author qingsheng.chen@hand-china.com
 * note1 : Git repository must has branch master.
 * note2 : LINE_BREAKS and DIRECTORY_SPLIT depends on the operating system.
 */
public class JGitUtil {
    private static final String README = "README.md";
    private static final String TITLE_LEVEL = "## ";
    private static final String MESSAGE_LEVEL = "* ";
    private static final String DEFAULT_CHARSET = "UTF-8";
    // Depends on the operating system
    private static final String LINE_BREAKS = "\n";
    private static final String DIRECTORY_SPLIT = "/";

    /**
     * 返回一个 builder 实例
     *
     * @return builder
     */
    public static JGitRepositoryBuilder builder() {
        return new JGitRepositoryBuilder();
    }

    /**
     * 获取仓库，首先判断本地仓库是否存在，不存在则从远程Clone仓库
     *
     * @param builder 构建器
     * @return Git
     */
    private static Git getLatestRepository(JGitRepositoryBuilder builder) {
        Git git;
        File fLocalPath = new File(builder.localPath);
        if (!fLocalPath.exists())
            if (!fLocalPath.mkdirs())
                throw new RuntimeException("Git local path is not exists! Create this path failed : " + builder.localPath);
        if (!fLocalPath.isDirectory())
            throw new RuntimeException("Git local path is not a directory : " + builder.localPath);
        try {
            git = Git.open(fLocalPath);
            PullCommand pullCommand = git.pull().setRemoteBranchName(builder.branchName);
            if (builder.username != null && builder.password != null)
                pullCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(builder.username, builder.password));
            pullCommand.call();
        } catch (Exception e) {
            if (e instanceof RepositoryNotFoundException) {
                try {
                    CloneCommand cloneCommand = Git.cloneRepository()
                            .setURI(builder.remoteUri)
                            .setDirectory(fLocalPath)
                            .setBranch(builder.branchName);
                    if (builder.username != null && builder.password != null)
                        cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(builder.username, builder.password));
                    git = cloneCommand.call();
                } catch (GitAPIException e1) {
                    throw new RuntimeException("Clone git repository failed : " + builder.remoteUri + LINE_BREAKS + "because : " + e1.getMessage());
                }
            } else {
                throw new RuntimeException("Open git repository failed : " + builder.localPath);
            }
        }
        return git;
    }

    /**
     * 重写
     *
     * @param git      git
     * @param filePath 文件相对路径
     * @param message  message
     */
    public static void rewrite(Git git, String filePath, String message) {
        String repositoryPath = git.getRepository().getDirectory().getParent() + DIRECTORY_SPLIT;
        File file = new File(repositoryPath + filePath);
        UTF8Writer writer = null;
        try {
            checkFileTree(file);
            writer = new UTF8Writer(new FileOutputStream(file));
            writer.write(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 提交所有内容
     *
     * @param git     git
     * @param builder builder
     */
    public static void commitAndPush(Git git, JGitRepositoryBuilder builder) {
        try {
            git.add().addFilepattern(".").call();
            git.commit().setMessage(builder.commitMessage).call();
            PushCommand pushCommand = git.push();
            if (builder.username != null && builder.password != null)
                pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(builder.username, builder.password));
            pushCommand.call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 在README.md 文件首追加记录
     *
     * @param git         git
     * @param title       追加内容标题
     * @param messageList 追加内容
     */
    public static void readMeAppendOnStart(Git git, String title, List<String> messageList) {
        appendOneStart(git, README, title, messageList);
    }

    /**
     * 在文件首追加
     *
     * @param git         git
     * @param filePath    文件相对路径
     * @param title       追加内容标题
     * @param messageList 追加内容
     */
    public static void appendOneStart(Git git, String filePath, String title, List<String> messageList) {
        String repositoryPath = git.getRepository().getDirectory().getParent() + DIRECTORY_SPLIT;
        File sourceFile = new File(repositoryPath + filePath);
        File tmpFile;
        RandomAccessFile tmpFileIO = null;
        RandomAccessFile sourceFileIO = null;
        try {
            checkFileTree(sourceFile);
            tmpFile = new File(repositoryPath + filePath + ".tmp");
            checkFileTree(tmpFile);
            tmpFileIO = new RandomAccessFile(tmpFile, "rw");
            sourceFileIO = new RandomAccessFile(sourceFile, "rw");
            // 将源文件读入到临时文件中
            byte[] buffer = new byte[1024];
            int length;
            while ((length = sourceFileIO.read(buffer)) != -1) {
                tmpFileIO.write(buffer, 0, length);
            }
            // 从源文件首写入新增内容和原内容
            // 写入新增内容
            sourceFileIO.seek(0L);
            sourceFileIO.write((TITLE_LEVEL + title + LINE_BREAKS + LINE_BREAKS).getBytes(DEFAULT_CHARSET));
            for (String item : messageList) {
                sourceFileIO.write((MESSAGE_LEVEL + item + LINE_BREAKS).getBytes(DEFAULT_CHARSET));
            }
            sourceFileIO.write(LINE_BREAKS.getBytes(DEFAULT_CHARSET));
            // 写入原内容
            tmpFileIO.seek(0L);
            while ((length = tmpFileIO.read(buffer)) != -1) {
                sourceFileIO.write(buffer, 0, length);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (tmpFileIO != null) {
                try {
                    tmpFileIO.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (sourceFileIO != null) {
                try {
                    sourceFileIO.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (tmpFile != null && !tmpFile.delete())
            throw new RuntimeException("Delete tmp file failed! " + tmpFile);
    }

    /**
     * Git 构建器
     */
    public static class JGitRepositoryBuilder {
        /**
         * 远程仓库地址
         */
        String remoteUri;

        /**
         * 本地仓库地址
         */
        String localPath;

        /**
         * 分支，默认master
         */
        String branchName = "master";

        /**
         * 用户名
         */
        String username;

        /**
         * 密码
         */
        String password;

        /**
         * 提交消息
         */
        String commitMessage;

        private JGitRepositoryBuilder() {
        }

        public JGitRepositoryBuilder remoteUri(String remoteUri) {
            this.remoteUri = remoteUri;
            return this;
        }

        public JGitRepositoryBuilder localPath(String localPath) {
            this.localPath = localPath;
            return this;
        }

        public JGitRepositoryBuilder branchName(String branchName) {
            this.branchName = branchName;
            return this;
        }

        public JGitRepositoryBuilder username(String username) {
            this.username = username;
            return this;
        }

        public JGitRepositoryBuilder password(String password) {
            this.password = password;
            return this;
        }

        public JGitRepositoryBuilder commitMessage(String commitMessage) {
            this.commitMessage = commitMessage;
            return this;
        }

        public Git builder() {
            if (this.remoteUri == null)
                throw new RuntimeException("JGitRepositoryBuilder remoteUri must not be null.");
            if (this.localPath == null)
                throw new RuntimeException("JGitRepositoryBuilder localPath must not be null.");
            return getLatestRepository(this);
        }
    }

    private static void checkFileTree(File file) throws IOException {
        if (!file.exists()) {
            if (!file.getParentFile().exists())
                if (!file.getParentFile().mkdirs())
                    throw new RuntimeException("Git repository has't this directory! Create this directory failed : " + file.getParentFile());
            if (!file.createNewFile())
                throw new RuntimeException("Git repository has't this file! Create this file failed : " + file.getAbsolutePath());
        }
    }
}