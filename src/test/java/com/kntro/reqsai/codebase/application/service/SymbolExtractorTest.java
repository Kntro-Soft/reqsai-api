package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.service.SymbolExtractor.FileSymbols;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SymbolExtractor")
class SymbolExtractorTest {

    @Test
    @DisplayName("finds Spring endpoints with the class prefix, JPA entities and declarations")
    void spring() {
        FileSymbols s = SymbolExtractor.extract("src/main/java/acme/ReservationController.java", """
                @RestController
                @RequestMapping("/api/reservations")
                public class ReservationController {
                    @PostMapping
                    public Reservation create(@RequestBody Request r) { return null; }
                    @DeleteMapping("/{id}")
                    public void cancel(@PathVariable UUID id) { }
                }
                """);
        assertThat(s.endpoints()).contains("POST /api/reservations", "DELETE /api/reservations/{id}");
        assertThat(s.symbols()).contains("ReservationController", "create", "cancel");

        FileSymbols entity = SymbolExtractor.extract("Reservation.java", """
                @Entity
                @Table(name = "reservations")
                public class Reservation { }
                """);
        assertThat(entity.entities()).containsExactly("Reservation (reservations)");
    }

    @Test
    @DisplayName("finds Express routes, Angular routes and exported functions in TypeScript")
    void typescript() {
        FileSymbols api = SymbolExtractor.extract("src/reservations/reservation.routes.ts", """
                export const router = Router();
                router.post('/reservations', create);
                router.delete("/reservations/:id", cancel);
                export function cancelReservation(id: string) { }
                """);
        assertThat(api.endpoints()).contains("POST /reservations", "DELETE /reservations/:id");
        assertThat(api.symbols()).contains("router", "cancelReservation");

        FileSymbols ui = SymbolExtractor.extract("src/app/app.routes.ts", """
                export const routes: Routes = [{ path: 'reservas', component: X }, { path: 'pagos/:id' }];
                """);
        assertThat(ui.endpoints()).contains("UI /reservas", "UI /pagos/:id");
    }

    @Test
    @DisplayName("finds FastAPI routes, SQL tables and Python classes")
    void pythonAndSql() {
        FileSymbols py = SymbolExtractor.extract("app/main.py", """
                @app.get("/menu")
                def menu():
                    pass
                class Reservation(Base):
                    pass
                """);
        assertThat(py.endpoints()).contains("GET /menu");
        assertThat(py.symbols()).contains("menu", "Reservation");
        assertThat(py.entities()).contains("Reservation");

        FileSymbols sql = SymbolExtractor.extract("db/001_init.sql",
                "CREATE TABLE IF NOT EXISTS reservations (id uuid);\ncreate table payments (id uuid);");
        assertThat(sql.entities()).containsExactly("reservations", "payments");
    }
}
