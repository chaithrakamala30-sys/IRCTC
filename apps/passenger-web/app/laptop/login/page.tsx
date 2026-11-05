import { PassengerIdentityExperience } from "../../../components/identity-experience";

export const metadata = { title: "Sign in — Laptop passenger" };

export default function LaptopLoginPage() {
  return <PassengerIdentityExperience device="laptop" mode="login" />;
}
