package Database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MicroblogDatabase {
    public static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    public static final String DB_URL = "jdbc:mysql://localhost:3307/db_microblog";
    public static final String USER = "root";
    public static final String PASS = "";

    public static Connection conn;

    static {
        try {
            Class.forName(JDBC_DRIVER);
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
        } catch (SQLException | ClassNotFoundException throwables) {
            throwables.printStackTrace();
        }
    }

    public MicroblogDatabase() throws SQLException, ClassNotFoundException {}

    public static void PUBLISH(String username, String header, String content) throws SQLException, ClassNotFoundException {
        assert(Authentification(username));
        String sql1 = "INSERT INTO messages (username, header, content) VALUES (?, ?, ?)";

        PreparedStatement pstmt1 = null;

        ArrayList<String> tags = new ArrayList<>();
        Pattern pattern = Pattern.compile("#\\w+");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String tag = matcher.group();
            tags.add(tag);
        }

        try {
            conn.setAutoCommit(false); // Start transaction

            pstmt1 = conn.prepareStatement(sql1);
            pstmt1.setString(1, username);
            pstmt1.setString(2, header);
            pstmt1.setString(3, content);
            pstmt1.executeUpdate();

            // Insert tags for the message
            int messageID = GET_LAST_MSG_ID(); // Get the ID of the last inserted message
            for (String tag : tags) {
                ADD_TAG(messageID,tag);
            }

            conn.commit(); // Commit the transaction
        } finally {
            if (pstmt1 != null) pstmt1.close();
        }
    }

    public static int GET_LAST_MSG_ID() throws SQLException, ClassNotFoundException {
        int lastMessageID = -1;
        String sql = "SELECT MAX(id) as max_id FROM messages";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) {
            lastMessageID = rs.getInt("max_id");
        }
        return lastMessageID;
    }

    public static boolean  GET_MSG_BY_ID(int msg_id) throws SQLException, ClassNotFoundException {
        String sql = "SELECT * FROM messages WHERE id = ?";

        PreparedStatement stmt = MicroblogDatabase.conn.prepareStatement(sql);
        stmt.setInt(1, msg_id);
        ResultSet rs = stmt.executeQuery();

        if (!rs.isBeforeFirst()) {
            System.out.println("No message found with id " + msg_id);
            return false;
        }

        String author = null, content = null, header = null, date = null;
        Integer reply_to_id = null;

        while (rs.next()) {
            author = rs.getString("username");
            header = rs.getString("header");
            content = rs.getString("content");
            date = rs.getString("timestamp");
            reply_to_id = rs.getInt("reply_to");
            if (rs.wasNull()) {
                reply_to_id = null;
            }
        }
        String response = ">> MSG \nauthor:" + author + " msg_id:" + msg_id;
        if (reply_to_id != null) response += " reply_to_id:" + reply_to_id;
        if (header.contains("REPUBLISH")) response += " republished:true";

        System.out.println(response);
        System.out.println(content);
        System.out.println(date);
        System.out.println();
        return true;
    }

    public static String GET_MSG_AUTHOR(int msg_id) throws SQLException {
        String sql = "SELECT * FROM messages WHERE id = ?";

        PreparedStatement stmt = MicroblogDatabase.conn.prepareStatement(sql);
        stmt.setInt(1, msg_id);
        ResultSet rs = stmt.executeQuery();

        if (!rs.isBeforeFirst()) {
            System.out.println("No message found with id " + msg_id);
            return null;
        }

        return rs.getString("username");
    }

    public static void REPLY(String username, String header, String content, String reply_to_id) throws SQLException, ClassNotFoundException {
        assert(Authentification(username));
        assert(GET_MSG_BY_ID(Integer.parseInt(reply_to_id)));

        String sql = "INSERT INTO messages (username, header, content, reply_to) VALUES (?, ?, ?, ?)";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, username);
        pstmt.setString(2, header);
        pstmt.setString(3, content);
        pstmt.setInt(4, Integer.parseInt(reply_to_id));
        pstmt.executeUpdate();
    }

    private static boolean HAS_TAG(String tag) throws SQLException, ClassNotFoundException {
        boolean exist = false;

        String sql = "SELECT COUNT(*) FROM tags WHERE (tag = ?)";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, tag);

        // Execute the query and check if the tag exists in the database
        ResultSet rs = pstmt.executeQuery();
        rs.next();
        int count = rs.getInt(1);
        if (count > 0) exist = true;
        rs.close();

        return exist;
    }

    private static void NEW_TAG(String tag) throws SQLException, ClassNotFoundException {
        if(HAS_TAG(tag)) return;
        String sql = "INSERT INTO tags (tag) VALUES (?)";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, tag);
        pstmt.executeUpdate();
    }

    public static void ADD_TAG(int id, String tag) throws SQLException, ClassNotFoundException {
        NEW_TAG(tag);
        String sql = "INSERT INTO message_tags (message_id, tag_name) VALUES (?, ?)";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, String.valueOf(id));
        pstmt.setString(2, tag);
        pstmt.executeUpdate();
    }

    public static void UNSUBSCRIBE_TAG(int id, String tag) throws SQLException, ClassNotFoundException {
        if(!HAS_TAG(tag)) {
            System.out.println("this tag does not exist");
            return;
        }
        String sql = "DELETE FROM tag_followers WHERE user_id=? AND tag_name=?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, String.valueOf(id));
        pstmt.setString(2, tag);
        pstmt.executeUpdate();
    }

    public static void SUBSCRIBE_TAG(int id, String tag) throws SQLException, ClassNotFoundException {
        if(!HAS_TAG(tag)) {
            System.out.println("this tag does not exist");
            return;
        }
        String sql = "INSERT INTO tag_followers (user_id, tag_name) VALUES (?, ?)";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, String.valueOf(id));
        pstmt.setString(2, tag);
        pstmt.executeUpdate();
    }

    public static Set<String> GET_TAG_FOLLOWERS(String tagName) throws SQLException, ClassNotFoundException {
        String query = "SELECT users.username " +
                "FROM tag_followers " +
                "JOIN users ON tag_followers.user_id = users.id " +
                "WHERE tag_followers.tag_name = ?";
        PreparedStatement pstmt = conn.prepareStatement(query);
        pstmt.setString(1, tagName);
        ResultSet rs = pstmt.executeQuery();

        Set<String> followers = new HashSet<>();
        while (rs.next()) {
            String follower = rs.getString("username");
            followers.add(follower);
        }

        return followers;
    }

    public static Set<String> GET_MSG_TAGS(int messageId) throws SQLException {
        Set<String> tags = new HashSet<>();

        PreparedStatement stmt = conn.prepareStatement("SELECT tag_name FROM message_tags WHERE message_id = ?");
        stmt.setInt(1, messageId);
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                tags.add(rs.getString("tag_name"));
            }
        }

        return tags;
    }

    public static void SUBSCRIBE_USER(int id1, int id2) throws SQLException, ClassNotFoundException {
        /*
        try{
            // Create a statement to execute a SQL query
            String query = "SELECT COUNT(*) FROM users WHERE (id = ?)";
            PreparedStatement stmt = conn.prepareStatement(query);

            // Set the client's name as a parameter in the query
            stmt.setInt(1, id2);

            // Execute the query and check if the client's id exists in the database
            ResultSet rs = stmt.executeQuery();
            rs.next();
            int count = rs.getInt(1);
            if (count > 0) {
                String sql = "INSERT INTO user_followers (follower_id, followee_id) VALUES (?, ?)";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, id1);
                pstmt.setInt(2, id2);
                pstmt.executeUpdate();
            } else {
                System.out.println("user does not exist");
            }
        } catch (SQLException e){
            System.out.println("user does not exist");
        }
         */

        if(Authentification(id2)){
            String sql = "INSERT INTO user_followers (follower_id, followee_id) VALUES (?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, id1);
            pstmt.setInt(2, id2);
            pstmt.executeUpdate();
        } else {
            System.out.println("user does not exist");
        }

    }

    public static void UNSUBSCRIBE_USER(int id1, int id2) throws SQLException, ClassNotFoundException {
        // Create a statement to execute a SQL query
        String query = "SELECT COUNT(*) FROM user_followers WHERE (follower_id=? AND followee_id=?)";
        PreparedStatement stmt = conn.prepareStatement(query);

        // Set the client's name as a parameter in the query
        stmt.setInt(1, id1);
        stmt.setInt(2, id2);

        // Execute the query and check if the client's id exists in the database
        ResultSet rs = stmt.executeQuery();
        rs.next();
        int count = rs.getInt(1);
        if (count > 0) {
            String sql = "DELETE FROM user_followers WHERE follower_id=? AND followee_id=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, id1);
            pstmt.setInt(2, id2);
            pstmt.executeUpdate();
        } else {
            System.out.println("user does not exist");
        }

    }

    public static Set<String> GET_USER_FOLLOWERS(String username) throws SQLException, ClassNotFoundException {
        Set<String> followers = new HashSet<>();
        String query = "SELECT u.username " +
                "FROM users u " +
                "JOIN user_followers f ON u.id = f.follower_id " +
                "WHERE f.followee_id = (SELECT id FROM users WHERE username = ?)";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, username);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            followers.add(rs.getString("username"));
        }
        rs.close();
        stmt.close();
        return followers;
    }

    public void close() throws SQLException {
        conn.close();
    }

    public static boolean Authentification(String username) throws SQLException, ClassNotFoundException {
        boolean exists = false;
        try {
            // Create a statement to execute a SQL query
            String query = "SELECT COUNT(*) FROM users WHERE (username = ?)";
            PreparedStatement stmt = conn.prepareStatement(query);

            // Set the client's name as a parameter in the query
            stmt.setString(1, username);

            // Execute the query and check if the client's name exists in the database
            ResultSet rs = stmt.executeQuery();
            rs.next();
            int count = rs.getInt(1);
            if (count > 0) {
                exists = true;
            }

            // Close the database connection and release resources
            rs.close();
            stmt.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            System.out.println("User doesn't exist");
        }
        return exists;
    }

    public static boolean Authentification(int user_id) {
        boolean exists = false;
        try {
            // Create a statement to execute a SQL query
            String query = "SELECT COUNT(*) FROM users WHERE (id = ?)";
            PreparedStatement stmt = conn.prepareStatement(query);

            // Set the client's name as a parameter in the query
            stmt.setInt(1, user_id);

            // Execute the query and check if the client's id exists in the database
            ResultSet rs = stmt.executeQuery();
            rs.next();
            int count = rs.getInt(1);
            if (count > 0) {
                exists = true;
            }

            // Close the database connection and release resources
            rs.close();
            stmt.close();

        } catch (SQLException ex) {
            ex.printStackTrace();
            System.out.println("User doesn't exist");
        }

        return exists;
    }

    public static void SignUp() throws SQLException, IOException {
        System.out.println("Do you want to sign up? yes/no");
        System.out.print("select: ");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        String answer = in.readLine();

        if(answer.equals("yes")) {
            System.out.print("Create your username (begin with '@'): ");
            String username = in.readLine();
            assert(username.charAt(0) == '@');

            String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            pstmt.setString(2, "");
            pstmt.executeUpdate();
            System.out.println("User inserted: " + username);
        }else{
            System.out.println(">> Cannot connect to server\n");
        }
    }

    public static int GET_USER_ID(String user) throws SQLException {
        String sql = "SELECT id FROM users WHERE username = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, user);
        ResultSet rs = pstmt.executeQuery();
        int id = -1;
        if (rs.next()) {
            id = rs.getInt("id");
        }
        rs.close();
        return id;
    }

    public static void main(String[] args) {
        try {
            //db.SignUp("james");
            //db.insertMessage("john", "Hello World!");
            //System.out.println(Authentification("@dean"));
            //System.out.println(Authentification("@james"));
            System.out.println(GET_USER_FOLLOWERS("@dean"));
            System.out.println(GET_TAG_FOLLOWERS("#wedding"));
            System.out.println(GET_MSG_TAGS(11));
            GET_MSG_BY_ID(28);

        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
