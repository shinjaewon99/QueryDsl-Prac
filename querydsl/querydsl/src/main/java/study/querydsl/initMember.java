package study.querydsl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.Team;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Profile("local")
@Component
@RequiredArgsConstructor
public class initMember {

    private final InitMemberService initMemberService;

    @PostConstruct
    public void init(){
        // DB에 데이터를 넣는다.
        initMemberService.init();
    }
    @Component
    static class InitMemberService {
        @PersistenceContext
        private EntityManager em;


        @Transactional
        public void init() {
            Team teamA = new Team("teamA");
            Team teamB = new Team("teamB");
            em.persist(teamA);
            em.persist(teamB);

            for (int i = 0; i < 100; i++) {
                Team selectedTeam;
                if (i % 2 == 0) selectedTeam = teamA;
                else selectedTeam = teamB;
                em.persist(new Member("member" + i, i, selectedTeam));

            }
        }
    }

}
