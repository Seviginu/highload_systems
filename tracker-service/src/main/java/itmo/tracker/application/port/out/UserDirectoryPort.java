package itmo.tracker.application.port.out;

public interface UserDirectoryPort {
    void requireUsers(Long... ids);

    void requireTeamLead(Long id);
}
