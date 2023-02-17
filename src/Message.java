import java.util.List;

class Message {
    private long id;
    private String user;
    private String content;
    private List<String> tags;

    public Message(long id, String user, String content, List<String> tags) {
        this.id = id;
        this.user = user;
        this.content = content;
        this.tags = tags;
    }

    public long getId() {
        return id;
    }

    public String getUser() {
        return user;
    }

    public String getContent() {
        return content;
    }

    public List<String> getTags() {
        return tags;
    }
}
